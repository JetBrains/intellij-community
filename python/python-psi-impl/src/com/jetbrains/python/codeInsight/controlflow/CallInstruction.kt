package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext

class CallInstruction(builder: ControlFlowBuilder, call: PyCallExpression) : InstructionImpl(builder, call) {
  override fun getElement(): PyCallExpression {
    return super.getElement() as PyCallExpression
  }

  fun isNoReturnCall(context: TypeEvalContext): Boolean {
    val callees = element.multiResolveCalleeFunction(PyResolveContext.defaultContext(context))
    if (callees.size == 1) {
      val pyFunction = callees.single()
      if (pyFunction is PyFunction && hasReturnTypeAnnotation(pyFunction)) {
        return context.getReturnType(pyFunction) is PyNeverType
      }
      // Fallback: look into the callee's body control-flow. If the function cannot
      // complete normally (no path reaches the function exit), treat it as no-return.
      if (pyFunction is PyFunction) {
        return functionIsEffectivelyNoReturn(pyFunction)
      }
    }
    return false
  }
}

private fun hasReturnTypeAnnotation(function: PyFunction): Boolean {
  return function.annotation != null || function.typeCommentAnnotation != null
}

private fun functionIsEffectivelyNoReturn(function: PyFunction): Boolean {
  // Cache per-function, drop on any PSI modification
  val manager = CachedValuesManager.getManager(function.project)
  return manager.getCachedValue(function) {
    val flow = ControlFlowCache.getControlFlow(function)
    val instructions = flow.instructions

    // Heuristic/performance guard: don't analyze very large CFGs
    // (extremely unlikely small helper functions will exceed this)
    val maxInstructionsToAnalyze = 1000
    val result = if (instructions.isEmpty() || instructions.size > maxInstructionsToAnalyze) {
      false
    } else {
      !exitIsReachable(instructions)
    }

    CachedValueProvider.Result.create(result, function)
  }
}

private fun exitIsReachable(instructions: Array<Instruction>): Boolean {
  // Start is always at index 0; exit node is the last instruction
  val start = instructions.first()
  val exit = instructions.last()
  // Simple iterative DFS without version checks (consistent with CFG text tests)
  val visited = BooleanArray(instructions.size)
  val stack = java.util.ArrayDeque<Instruction>()
  stack.addFirst(start)

  var steps = 0
  val maxSteps = 20000 // safety guard against pathological graphs

  while (!stack.isEmpty() && steps++ < maxSteps) {
    val insn = stack.removeFirst()
    val num = insn.num()
    if (visited[num]) continue
    visited[num] = true
    if (insn === exit) return true
    val succs = insn.allSucc()
    for (succ in succs) {
      if (!visited[succ.num()]) stack.addFirst(succ)
    }
  }
  return visited[exit.num()]
}