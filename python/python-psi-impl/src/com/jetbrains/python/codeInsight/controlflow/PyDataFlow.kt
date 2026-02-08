package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque

data class FlowContext(val typeEvalContext: TypeEvalContext, val checkNoReturnCalls: Boolean)

@ApiStatus.Internal
enum class Reachability {
  REACHABLE,
  UNREACHABLE,
  UNREACHABLE_FOR_TYPE_CHECKING
}

@ApiStatus.Internal
class PyDataFlow(private val controlFlow: PyControlFlow, context: FlowContext) : ControlFlow by controlFlow {
  private val reachability: Array<Reachability>

  init {
    val reachabilityStrict = computeReachability(instructions, false, context)
    val reachabilitySoft = computeReachability(instructions, true, context)

    reachability = Array(instructions.size) { i ->
      if (reachabilityStrict[i]) {
        assert(reachabilitySoft[i])
        Reachability.REACHABLE
      }
      else if (reachabilitySoft[i]) {
        Reachability.UNREACHABLE_FOR_TYPE_CHECKING
      }
      else {
        Reachability.UNREACHABLE
      }
    }
  }

  private fun computeReachability(
    instructions: Array<Instruction>,
    includeUnreachableForTypeChecking: Boolean,
    context: FlowContext,
  ): BooleanArray {
    val reachability = BooleanArray(instructions.size)
    val stack = ArrayDeque<Instruction>()
    stack.push(instructions[0])

    while (stack.isNotEmpty()) {
      val instruction = stack.pop()
      val instructionNum = instruction.num()

      if (reachability[instructionNum]) {
        continue // Already visited
      }

      reachability[instructionNum] = true

      for (successor in getReachableSuccessors(instruction, includeUnreachableForTypeChecking, context)) {
        if (!reachability[successor.num()]) {
          stack.push(successor)
        }
      }
    }
    return reachability
  }

  private fun getReachableSuccessors(
    instruction: Instruction,
    includeUnreachableForTypeChecking: Boolean,
    context: FlowContext
  ): Collection<Instruction> {
    if (context.checkNoReturnCalls && instruction is CallInstruction && instruction.isNoReturnCall(context.typeEvalContext)) return emptyList()
    if (instruction is PyWithContextExitInstruction && !instruction.isSuppressingExceptions(context.typeEvalContext)) return emptyList()
    return instruction.allSucc().filter {
      if (it is PyUnreachableInstruction) {
        return@filter includeUnreachableForTypeChecking && it.isUnreachableForTypeChecking
      }
      if (it is ReadWriteInstruction && it.access.isAssertTypeAccess) {
        val type = it.getType(context.typeEvalContext, null)
        return@filter !(type != null && type.get() is PyNeverType)
      }
      return@filter true
    }
  }

  fun getReachability(instruction: Instruction): Reachability {
    if (instruction.num() >= reachability.size) return Reachability.REACHABLE
    return reachability[instruction.num()]
  }

  fun getInstruction(element: PsiElement): Int {
    return controlFlow.getInstruction(element)
  }
}

/**
 * Checks if inspections should flag a Python element as unreachable.
 *
 * This method considers special cases where code might be technically unreachable
 * but should not be reported as an issue.
 * In particular, the first terminating statement in a sequence is considered valid
 * and should not be reported as unreachable.
 *
 * Terminating statements include:
 * - `raise` statements
 * - `assert False`
 * - calls to functions annotated with `NoReturn`
 */
fun PsiElement.getReachabilityForInspection(context: TypeEvalContext): Reachability {
  return getReachabilityForInspection(FlowContext(context, true))
}

private fun PsiElement.getReachabilityForInspection(context: FlowContext): Reachability {
  return PyUtil.getParameterizedCachedValue(this, context) { getReachabilityForInspectionNoCache(it) }
}

private fun PsiElement.getReachabilityForInspectionNoCache(context: FlowContext): Reachability {
  if (parent is PyElement) {
    val reachability = parent.getReachabilityForInspection(context)
    if (reachability != Reachability.REACHABLE) {
      return reachability
    }
  }
  val reachability = getReachabilityByControlFlow(context)
  if (reachability == Reachability.REACHABLE) {
    return Reachability.REACHABLE
  }
  val isIgnoredUnreachableStatement = when (this) {
    is PyStatementList -> (statements.firstOrNull()?.isIgnoredUnreachableStatement(context) ?: true)
    is PyStatement -> this.isIgnoredUnreachableStatement(context)
    else -> true
  }
  return if (isIgnoredUnreachableStatement) Reachability.REACHABLE else reachability
}

/**
 * Determines if the element is unreachable by control flow analysis.
 * If the element does not have corresponding instruction in CFG, searches for the nearest parent that has.
 */
fun PsiElement.isUnreachableByControlFlow(context: TypeEvalContext): Boolean {
  return getReachabilityByControlFlow(FlowContext(context, true)) != Reachability.REACHABLE
}

private fun PsiElement.getReachabilityByControlFlow(context: FlowContext): Reachability {
  return PyUtil.getParameterizedCachedValue(this, context) { this.getReachabilityByControlFlowNoCache(it) }
}

private fun PsiElement.getReachabilityByControlFlowNoCache(context: FlowContext): Reachability {
  val scope = ScopeUtil.getScopeOwner(this)
  if (scope != null) {
    val flow = ControlFlowCache.getDataFlow(scope, context)
    val instructions = flow.instructions
    val idx = flow.getInstruction(this)
    if (idx < 0 || instructions[idx].isAuxiliary()) {
      val parent = this.parent
      if (parent == null) {
        return Reachability.REACHABLE
      }
      return parent.getReachabilityByControlFlow(context)
    }
    return flow.getReachability(instructions[idx])
  }
  return Reachability.REACHABLE
}

private fun PyStatement.isIgnoredUnreachableStatement(context: FlowContext): Boolean {
  val parentBlock = this.parent as? PyStatementList ?: return false
  if (parentBlock.statements[0] != this) return false
  val parentCompoundStatement = parentBlock.findParentOfType<PyStatement>() ?: return false
  return parentCompoundStatement.getReachabilityByControlFlow(context) == Reachability.REACHABLE && isTerminatingStatement(context.typeEvalContext)
}

private fun PsiElement.isTerminatingStatement(context: TypeEvalContext): Boolean {
  return when (this) {
    is PyRaiseStatement -> true
    is PyAssertStatement -> getArguments().firstOrNull()?.asBooleanNoResolve() == false
    is PyExpressionStatement -> expression is PyCallExpression && context.getType(expression) is PyNeverType
    else -> false
  }
}

private fun Instruction.isAuxiliary(): Boolean {
  return when (this) {
    is PyRaiseInstruction -> true
    is PyWithContextExitInstruction -> true
    is PyFinallyFailExitInstruction -> true
    is ReadWriteInstruction -> access.isAssertTypeAccess
    else -> false
  }
}

private fun PyExpression.asBooleanNoResolve(): Boolean? {
  return PyEvaluator.evaluateAsBooleanNoResolve(this)
}
