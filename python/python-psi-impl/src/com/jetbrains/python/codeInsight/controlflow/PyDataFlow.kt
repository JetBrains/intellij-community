// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ConditionalInstruction
import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.util.ThreeState
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypeCommentOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque

/**
 * @param evaluateConditionTypes whether to treat a branch as unreachable when the static type of its condition
 *   guarantees the opposite truthiness (e.g. the `then` branch of `if a:` where `a` is always falsy because its
 *   `__bool__` returns a false literal).
 *   Enabled only for inspection-facing reachability to keep the extra type evaluation out of hot paths like
 *   reaching-definitions analysis.
 */
data class FlowContext @JvmOverloads constructor(
  val typeEvalContext: TypeEvalContext,
  val checkNoReturnCalls: Boolean,
  val evaluateConditionTypes: Boolean = false,
)

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
    if (context.evaluateConditionTypes && instruction is ConditionalInstruction && isContradictedByConditionType(instruction, context.typeEvalContext)) {
      return emptyList()
    }
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

  /**
   * Returns `true` if the static type of the condition guarantees that the branch corresponding to
   * [instruction] can never be taken, e.g. the `then` branch of `if a:` where `a` is always falsy
   * (its `__bool__` returns a false literal).
   * The type does not count when the condition reads a variable that another scope can assign, see [readsVariableOfOtherScope].
   *
   * A `TYPE_CHECKING` condition is never contradicted, whatever its type.
   */
  private fun isContradictedByConditionType(instruction: ConditionalInstruction, context: TypeEvalContext): Boolean {
    val condition = instruction.condition as? PyExpression ?: return false
    if (PyEvaluator.isTypeCheckingExpression(PyPsiUtils.flattenParens(condition))) return false
    val truthiness = PyTypeUtil.getTypeTruthiness(context.getType(condition), condition, context)
    if (truthiness == ThreeState.UNSURE) return false
    if (readsVariableOfOtherScope(condition, context)) return false
    return instruction.result != (truthiness == ThreeState.YES)
  }

  /**
   * Returns `true` if [condition] reads a variable without a declared type that another scope can assign:
   * a variable of an outer scope, or a variable of the scope of [condition] that a nested scope rebinds with `global` or `nonlocal`.
   * The inferred literal type of such a variable, as `Literal[False]` of `flag = False`, comes from one assignment only.
   */
  private fun readsVariableOfOtherScope(condition: PyExpression, context: TypeEvalContext): Boolean {
    val owner = ScopeUtil.getScopeOwner(condition) ?: return false
    val resolveContext = PyResolveContext.defaultContext(context)
    return SyntaxTraverser.psiTraverser(condition).filter(PyReferenceExpression::class.java).any { reference ->
      val name = reference.referencedName
      !reference.isQualified && name != null &&
      reference.getReference(resolveContext).multiResolve(false).any { result ->
        val variable = result.element
        (variable is PyTargetExpression || variable is PyNamedParameter) &&
        !hasDeclaredType(variable) &&
        (!isScopeOfCondition(ScopeUtil.getScopeOwner(variable), owner, condition) || ScopeUtil.isReboundInNestedScope(owner, name))
      }
    }
  }

  private fun isScopeOfCondition(variableOwner: ScopeOwner?, owner: ScopeOwner, condition: PyExpression): Boolean {
    // A comprehension or a lambda in the condition has its own scope.
    return variableOwner == owner || variableOwner != null && PsiTreeUtil.isAncestor(condition, variableOwner, false)
  }

  private fun hasDeclaredType(variable: PsiElement): Boolean {
    return variable is PyAnnotationOwner && variable.annotationValue != null ||
           variable is PyTypeCommentOwner && variable.typeCommentAnnotation != null
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
  return getReachabilityForInspection(FlowContext(context, checkNoReturnCalls = true, evaluateConditionTypes = true))
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
@ApiStatus.Internal
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
    is PyAssertStatement -> arguments.firstOrNull()?.asBooleanNoResolve() == false
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
