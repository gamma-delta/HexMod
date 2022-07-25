package at.petrak.hexcasting.common.casting.operators.spells

import at.petrak.hexcasting.api.misc.ManaConstants
import at.petrak.hexcasting.api.spell.*
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.iota.Iota
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.Vec3

object OpDestroyWater : SpellAction {
    override val argc = 1
    override fun execute(
        args: List<Iota>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val target = args.getBlockPos(0, argc)
        ctx.assertVecInRange(target)

        return Triple(
            Spell(target),
            2 * ManaConstants.CRYSTAL_UNIT,
            listOf(ParticleSpray.burst(Vec3.atCenterOf(target), 3.0))
        )
    }

    const val MAX_DESTROY_COUNT = 1024

    private data class Spell(val basePos: BlockPos) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            // SpongeBlock.java
            val todo = ArrayDeque<BlockPos>()
            val seen = HashSet<BlockPos>()

            // a little extra range on the initial cast to make it feel more intuitive
            for (xShift in -2..2) for (yShift in -2..2) for (zShift in -2..2) {
                todo.add(basePos.offset(xShift, yShift, zShift))
            }

            var successes = 0
            while (todo.isNotEmpty() && successes <= MAX_DESTROY_COUNT) {
                val here = todo.removeFirst()
                val distFromFocus =
                    ctx.caster.position().distanceToSqr(Vec3.atCenterOf(here))
                if (distFromFocus < Action.MAX_DISTANCE * Action.MAX_DISTANCE
                    && seen.add(here)
                    && ctx.world.mayInteract(ctx.caster, here)
                ) {
                    // never seen this pos in my life
                    val fluid = ctx.world.getFluidState(here)
                    if (fluid != Fluids.EMPTY.defaultFluidState()) {
                        val blockstate = ctx.world.getBlockState(here)
                        if (IXplatAbstractions.INSTANCE.isBreakingAllowed(
                                ctx.world,
                                here,
                                blockstate,
                                ctx.caster
                            )
                        ) {
                            val material = blockstate.material
                            val success =
                                if (blockstate.block is BucketPickup && !(blockstate.block as BucketPickup).pickupBlock(
                                        ctx.world,
                                        here,
                                        blockstate
                                    ).isEmpty
                                ) {
                                    true
                                } else if (blockstate.block is LiquidBlock) {
                                    ctx.world.setBlock(here, Blocks.AIR.defaultBlockState(), 3)
                                    true
                                } else if (material == Material.WATER_PLANT || material == Material.REPLACEABLE_WATER_PLANT) {
                                    val blockentity: BlockEntity? =
                                        if (blockstate.hasBlockEntity()) ctx.world.getBlockEntity(here) else null
                                    Block.dropResources(blockstate, ctx.world, here, blockentity)
                                    ctx.world.setBlock(here, Blocks.AIR.defaultBlockState(), 3)
                                    true
                                } else {
                                    false
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
                                for (dir in Direction.values()) {
                                    todo.add(here.relative(dir))
                                }
                            }
                        }
                    }
                }
            }

            if (successes > 0) {
                ctx.world.playSound(
                    null,
                    basePos.x.toDouble(),
                    basePos.y.toDouble(),
                    basePos.z.toDouble(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS,
                    1.0f,
                    0.95f
                )
            }
        }
    }
}
