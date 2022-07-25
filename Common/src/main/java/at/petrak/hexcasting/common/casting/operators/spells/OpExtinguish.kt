package at.petrak.hexcasting.common.casting.operators.spells

import at.petrak.hexcasting.api.misc.ManaConstants
import at.petrak.hexcasting.api.spell.*
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.iota.Iota
import at.petrak.hexcasting.ktxt.UseOnContext
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object OpExtinguish : SpellAction {
    override val argc = 1
    override fun execute(
        args: List<Iota>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val target = args.getBlockPos(0, argc)
        ctx.assertVecInRange(target)

        return Triple(
            Spell(target),
            ManaConstants.DUST_UNIT * 6,
            listOf(ParticleSpray.burst(Vec3.atCenterOf(target), 1.0))
        )
    }

    const val MAX_DESTROY_COUNT = 1024

    private data class Spell(val target: BlockPos) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            // how many levels of "borrowed" code are we on now
            val todo = ArrayDeque<BlockPos>()
            val seen = HashSet<BlockPos>()
            todo.add(target)

            var successes = 0
            while (todo.isNotEmpty() && successes <= MAX_DESTROY_COUNT) {
                val here = todo.removeFirst()
                val distFromFocus =
                    ctx.caster.position().distanceToSqr(Vec3.atCenterOf(here))
                val distFromTarget = target.distSqr(here) // max distance to prevent runaway shenanigans
                if (distFromFocus < Action.MAX_DISTANCE * Action.MAX_DISTANCE
                    && seen.add(here)
                    && distFromTarget < 10 * 10
                    && ctx.world.mayInteract(ctx.caster, here)
                ) {
                    // never seen this pos in my life
                    val blockstate = ctx.world.getBlockState(here)
                    if (IXplatAbstractions.INSTANCE.isBreakingAllowed(ctx.world, here, blockstate, ctx.caster)) {
                        val success =
                            when (blockstate.block) {
                                is BaseFireBlock -> {
                                    ctx.world.setBlock(here, Blocks.AIR.defaultBlockState(), 3); true
                                }
                                is CampfireBlock -> {
                                    if (blockstate.getValue(CampfireBlock.LIT)) { // check if campfire is lit before putting it out
                                        val wilson =
                                            Items.WOODEN_SHOVEL // summon shovel from the ether to do our bidding
                                        val hereVec = Vec3.atCenterOf(here)
                                        wilson.useOn(
                                            UseOnContext(
                                                ctx.world,
                                                null,
                                                InteractionHand.MAIN_HAND,
                                                ItemStack(wilson),
                                                BlockHitResult(hereVec, Direction.UP, here, false)
                                            )
                                        ); true
                                    } else false
                                }
                                is AbstractCandleBlock -> {
                                    if (blockstate.getValue(AbstractCandleBlock.LIT)) { // same check for candles
                                        AbstractCandleBlock.extinguish(null, blockstate, ctx.world, here); true
                                    } else false
                                }
                                is NetherPortalBlock -> {
                                    ctx.world.setBlock(here, Blocks.AIR.defaultBlockState(), 3); true
                                }
                                else -> false
                            }

                        if (success) {
                            ctx.world.sendParticles(
                                ParticleTypes.SMOKE,
                                here.x + 0.5 + Math.random() * 0.4 - 0.2,
                                here.y + 0.5 + Math.random() * 0.4 - 0.2,
                                here.z + 0.5 + Math.random() * 0.4 - 0.2,
                                2,
                                0.0,
                                0.05,
                                0.0,
                                0.0
                            )
                            successes++
                        }
                        for (dir in Direction.values()) {
                            todo.add(here.relative(dir))
                        }
                    }
                }
            }

            if (successes > 0) {
                ctx.world.playSound(
                    null,
                    target.x.toDouble(),
                    target.y.toDouble(),
                    target.z.toDouble(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS,
                    1.0f,
                    0.95f
                )
            }
        }
    }
}
