package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class PyDataFlow(controlFlow: ControlFlow, private val context: TypeEvalContext) : ControlFlow by controlFlow {
  private val reachability: BooleanArray = BooleanArray(instructions.size)

  init {
    buildReachability()
  }

  private fun buildReachability() {
    val toBeProcessed: Queue<Instruction> = ArrayDeque<Instruction>()
    toBeProcessed.add(instructions[0])
    while (!toBeProcessed.isEmpty()) {
      val instruction = toBeProcessed.poll()
      reachability[instruction.num()] = true
      for (successor in getReachableSuccessors(instruction)) {
        if (!reachability[successor.num()]) {
          toBeProcessed.add(successor)
        }
      }
    }
  }

  private fun getReachableSuccessors(instruction: Instruction): Collection<Instruction> {
    if (instruction is CallInstruction && instruction.isNoReturnCall(context)) return emptyList()
    if (instruction is PyWithContextExitInstruction && !instruction.isSuppressingExceptions(context)) return emptyList()
    return instruction.allSucc().filter { next: Instruction ->
      if (next is ReadWriteInstruction && next.access.isAssertTypeAccess) {
        val type = next.getType(context, null)
        return@filter !(type != null && type.get() is PyNeverType)
      }
      return@filter true
    }
  }

  fun isUnreachable(instruction: Instruction): Boolean {
    if (instruction.num() >= reachability.size) return false
    return !reachability[instruction.num()]
  }
}

fun PsiElement.isUnreachable(context: TypeEvalContext): Boolean {
  val scope = ScopeUtil.getScopeOwner(this)
  if (scope != null) {
    val flow = ControlFlowCache.getControlFlow(scope).getInstructions()
    val idx = ControlFlowUtil.findInstructionNumberByElement(flow, this)
    if (idx < 0) return false
    return ControlFlowCache.getDataFlow(scope, context).isUnreachable(flow[idx])
  }
  return false
}