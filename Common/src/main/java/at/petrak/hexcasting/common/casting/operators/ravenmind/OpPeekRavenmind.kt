package at.petrak.hexcasting.common.casting.operators.ravenmind

import at.petrak.hexcasting.api.spell.Action
import at.petrak.hexcasting.api.spell.OperationResult
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.casting.SpellContinuation
import at.petrak.hexcasting.api.spell.iota.Iota
import at.petrak.hexcasting.api.spell.orNull

object OpPeekRavenmind : Action {
    override fun operate(
        continuation: SpellContinuation,
        stack: MutableList<Iota>,
        ravenmind: Iota?,
        ctx: CastingContext
    ): OperationResult {
        stack.add(ravenmind.orNull())
        return OperationResult(continuation, stack, ravenmind, listOf())
    }
}
