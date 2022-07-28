package at.petrak.hexcasting.common.casting.operators.spells.great

import at.petrak.hexcasting.api.misc.ManaConstants
import at.petrak.hexcasting.api.player.FlightAbility
import at.petrak.hexcasting.api.spell.*
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.roundToInt

object OpFlight : SpellOperator {
    override val argc = 3
    override val isGreat = true
    override fun execute(
        args: List<SpellDatum<*>>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val target = args.getChecked<ServerPlayer>(0, argc)
        val timeRaw = max(args.getChecked(1, argc), 0.0)
        val radiusRaw = max(args.getChecked(2, argc), 0.0)
        ctx.assertEntityInRange(target)

        // Convert to ticks
        val time = (timeRaw * 20.0).roundToInt()
        return Triple(
            Spell(target, time, radiusRaw, ctx.position),
            (ManaConstants.DUST_UNIT * 0.25 * (timeRaw * radiusRaw + 1.0)).roundToInt(),
            listOf(ParticleSpray(target.position(), Vec3(0.0, 2.0, 0.0), 0.0, 0.1))
        )
    }

    data class Spell(val target: ServerPlayer, val time: Int, val radius: Double, val origin: Vec3) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            if (target.abilities.mayfly) {
                // Don't accidentally clobber someone else's flight
                return
            }

            IXplatAbstractions.INSTANCE.setFlight(
                target,
                FlightAbility(
                    true,
                    time,
                    target.level.dimension(),
                    origin,
                    radius
                )
            )

            target.abilities.mayfly = true
            target.abilities.flying = true
            target.onUpdateAbilities()
            // Launch the player into the air to really emphasize the flight
            target.push(0.0, 1.0, 0.0)
            target.hurtMarked = true // Whyyyyy
        }
    }

    fun tickDownFlight(entity: LivingEntity) {
        if (entity !is ServerPlayer) return

        val flight = IXplatAbstractions.INSTANCE.getFlight(entity)

        if (flight.allowed) {
            val flightTime = flight.timeLeft - 1
            if (flightTime < 0 || flight.origin.distanceToSqr(entity.position()) > flight.radius * flight.radius || flight.dimension != entity.level.dimension()) {
                if (!entity.isOnGround) {
                    entity.fallDistance = 1_000_000f
                }
                IXplatAbstractions.INSTANCE.setFlight(entity, FlightAbility.deny())

                if (!entity.isCreative && !entity.isSpectator) {
                    val abilities = entity.abilities
                    abilities.flying = false
                    abilities.mayfly = false
                    entity.onUpdateAbilities()
                }
            } else {
                if (!entity.abilities.mayfly) {
                    entity.abilities.mayfly = true
                    entity.onUpdateAbilities()
                }
                IXplatAbstractions.INSTANCE.setFlight(
                    entity,
                    FlightAbility(
                        true,
                        flightTime,
                        flight.dimension,
                        flight.origin,
                        flight.radius
                    )
                )
            }
        }

    }

    fun tickAllPlayers(world: ServerLevel) {
        for (player in world.players()) {
            tickDownFlight(player)
        }
    }
}
