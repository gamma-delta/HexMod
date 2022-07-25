package at.petrak.hexcasting.common.casting.operators.spells

import at.petrak.hexcasting.api.misc.ManaConstants
import at.petrak.hexcasting.api.spell.ParticleSpray
import at.petrak.hexcasting.api.spell.RenderedSpell
import at.petrak.hexcasting.api.spell.SpellAction
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.getBlockPos
import at.petrak.hexcasting.api.spell.iota.Iota
import at.petrak.hexcasting.api.spell.mishaps.MishapBadBlock
import at.petrak.hexcasting.common.misc.AkashicTreeGrower
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.Vec3

object OpEdifySapling : SpellAction {
    override val argc = 1

    override fun execute(
        args: List<Iota>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val pos = args.getBlockPos(0, argc)
        val bs = ctx.world.getBlockState(pos)
        if (!bs.`is`(BlockTags.SAPLINGS))
            throw MishapBadBlock.of(pos, "sapling")

        return Triple(
            Spell(pos),
            ManaConstants.CRYSTAL_UNIT,
            listOf(ParticleSpray(Vec3.atCenterOf(pos), Vec3(0.0, 2.0, 0.0), 0.1, Math.PI / 4, 100))
        )
    }

    private data class Spell(val pos: BlockPos) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            val blockstate = ctx.world.getBlockState(pos)
            if (!ctx.world.mayInteract(ctx.caster, pos) ||
                !IXplatAbstractions.INSTANCE.isBreakingAllowed(ctx.world, pos, blockstate, ctx.caster))
                return

            val bs = ctx.world.getBlockState(pos)
            for (i in 0 until 8) {
                val success = AkashicTreeGrower.INSTANCE.growTree(
                    ctx.world,
                    ctx.world.chunkSource.generator,
                    pos,
                    bs,
                    ctx.world.getRandom()
                )
                if (success) break
            }
        }
    }
}
