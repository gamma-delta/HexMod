package at.petrak.hexcasting.api.spell

import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.casting.OperatorSideEffect
import at.petrak.hexcasting.api.spell.casting.SpellContinuation
import at.petrak.hexcasting.api.spell.mishaps.MishapNotEnoughArgs

interface SpellOperator : Operator {
    val argc: Int

    fun hasCastingSound(ctx: CastingContext): Boolean = true

    fun awardsCastingStat(ctx: CastingContext): Boolean = true

    fun execute(
        args: List<SpellDatum<*>>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>>?

    fun consumeFromStack(stack: MutableList<SpellDatum<*>>, ctx: CastingContext): List<SpellDatum<*>>
    {
        if (this.argc > stack.size)
            throw MishapNotEnoughArgs(this.argc, stack.size)
        val args = stack.takeLast(this.argc)
        for (_i in 0 until this.argc) stack.removeLast()
        return args
    }
    override fun operate(continuation: SpellContinuation, stack: MutableList<SpellDatum<*>>, local: SpellDatum<*>, ctx: CastingContext): OperationResult {
        val args = consumeFromStack(stack, ctx)
        val executeResult = this.execute(args, ctx) ?: return OperationResult(continuation, stack, local, listOf())
        val (spell, mana, particles) = executeResult

        val sideEffects = mutableListOf<OperatorSideEffect>()

        if (mana > 0)
            sideEffects.add(OperatorSideEffect.ConsumeMana(mana))

        // Don't have an effect if the caster isn't enlightened, even if processing other side effects
        if (!isGreat || ctx.isCasterEnlightened)
            sideEffects.add(OperatorSideEffect.AttemptSpell(spell, this.hasCastingSound(ctx), this.awardsCastingStat(ctx)))

        for (spray in particles)
            sideEffects.add(OperatorSideEffect.Particles(spray))

        return OperationResult(continuation, stack, local, sideEffects)
    }

}
