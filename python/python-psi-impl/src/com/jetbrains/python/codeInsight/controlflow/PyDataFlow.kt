package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.getEffectiveLanguageLevel
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.evaluateVersionsForElement
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

data class FlowContext(val typeEvalContext: TypeEvalContext, val checkNoReturnCalls: Boolean)

@ApiStatus.Internal
class PyDataFlow(
  scopeOwner: ScopeOwner,
  private val controlFlow: PyControlFlow,
  context: FlowContext,
) : ControlFlow by controlFlow {
  private val reachability: BooleanArray = BooleanArray(instructions.size)

  init {
    val languageLevel = getEffectiveLanguageLevel(scopeOwner.containingFile)
    val languageVersion = Version(languageLevel.majorVersion, languageLevel.minorVersion, 0)

    val stack = ArrayDeque<Instruction>()
    stack.push(instructions[0])

    while (stack.isNotEmpty()) {
      val instruction = stack.pop()
      val instructionNum = instruction.num()

      if (reachability[instructionNum]) {
        continue // Already visited
      }

      reachability[instructionNum] = true

      for (successor in getReachableSuccessors(instruction, languageVersion, context)) {
        if (!reachability[successor.num()]) {
          stack.push(successor)
        }
      }
    }
  }

  private fun getReachableSuccessors(instruction: Instruction, languageVersion: Version, context: FlowContext): Collection<Instruction> {
    if (context.checkNoReturnCalls && instruction is CallInstruction && instruction.isNoReturnCall(context.typeEvalContext)) return emptyList()
    if (instruction is PyWithContextExitInstruction && !instruction.isSuppressingExceptions(context.typeEvalContext)) return emptyList()
    return instruction.allSucc()
      .filter { it.isReachableWithVersionChecks(languageVersion) }
      .filter { next: Instruction ->
        if (next is ReadWriteInstruction && next.access.isAssertTypeAccess) {
          val type = next.getType(context.typeEvalContext, null)
          return@filter !(type != null && type.get() is PyNeverType)
        }
        return@filter true
      }
  }

  fun isUnreachable(instruction: Instruction): Boolean {
    if (instruction.num() >= reachability.size) return false
    return !reachability[instruction.num()]
  }

  private fun Instruction.isReachableWithVersionChecks(languageVersion: Version): Boolean {
    return evaluateVersionsForElement(element ?: return true).contains(languageVersion)
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
fun PsiElement.isUnreachableForInspection(context: TypeEvalContext): Boolean {
  return isUnreachableForInspection(FlowContext(context, true))
}

private fun PsiElement.isUnreachableForInspection(context: FlowContext): Boolean {
  return PyUtil.getParameterizedCachedValue(this, context) { isUnreachableForInspectionNoCache(it) }
}

private fun PsiElement.isUnreachableForInspectionNoCache(context: FlowContext): Boolean {
  if (parent is PyElement && parent.isUnreachableForInspection(context)) return true
  return isUnreachableByControlFlow(context) && when (this) {
    is PyStatementList -> !(statements.firstOrNull()?.isIgnoredUnreachableStatement(context) ?: true)
    is PyStatement -> !this.isIgnoredUnreachableStatement(context)
    else -> false
  }
}

/**
 * Determines if the element is unreachable by control flow analysis.
 * If the element does not have corresponding instruction in CFG, searches for the nearest parent that has.
 */
fun PsiElement.isUnreachableByControlFlow(context: TypeEvalContext): Boolean {
  return isUnreachableByControlFlow(FlowContext(context, true))
}

private fun PsiElement.isUnreachableByControlFlow(context: FlowContext): Boolean {
  return PyUtil.getParameterizedCachedValue(this, context) { this.isUnreachableByControlFlowNoCache(it) }
}

private fun PsiElement.isUnreachableByControlFlowNoCache(context: FlowContext): Boolean {
  val scope = ScopeUtil.getScopeOwner(this)
  if (scope != null) {
    val flow = ControlFlowCache.getDataFlow(scope, context)
    val instructions = flow.instructions
    val idx = flow.getInstruction(this)
    if (idx < 0 || instructions[idx].isAuxiliary()) {
      val parent = this.parent
      return parent != null && parent.isUnreachableByControlFlow(context)
    }
    return flow.isUnreachable(instructions[idx])
  }
  return false
}

private fun PyStatement.isIgnoredUnreachableStatement(context: FlowContext): Boolean {
  val parentBlock = this.parent as? PyStatementList ?: return false
  if (parentBlock.statements[0] != this) return false
  val parentCompoundStatement = parentBlock.findParentOfType<PyStatement>() ?: return false
  return !parentCompoundStatement.isUnreachableByControlFlow(context) && isTerminatingStatement(context.typeEvalContext)
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
