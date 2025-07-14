package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.openapi.util.Version
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.getEffectiveLanguageLevel
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.evaluateVersionsForElement
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class PyDataFlow(scopeOwner: ScopeOwner, controlFlow: ControlFlow, private val context: TypeEvalContext) : ControlFlow by controlFlow {
  private val reachability: BooleanArray = BooleanArray(instructions.size)
  private val languageVersion = run {
    val languageLevel = getEffectiveLanguageLevel(scopeOwner.containingFile)
    Version(languageLevel.majorVersion, languageLevel.minorVersion, 0)
  }


  init {
    buildReachability()
  }

  private fun buildReachability() {
    val stack = ArrayDeque<Instruction>()
    stack.push(instructions[0])

    while (stack.isNotEmpty()) {
      val instruction = stack.pop()
      val instructionNum = instruction.num()

      if (reachability[instructionNum]) {
        continue // Already visited
      }

      reachability[instructionNum] = true

      for (successor in getReachableSuccessors(instruction)) {
        if (!reachability[successor.num()]) {
          stack.push(successor)
        }
      }
    }
  }

  private fun getReachableSuccessors(instruction: Instruction): Collection<Instruction> {
    if (instruction is CallInstruction && instruction.isNoReturnCall(context)) return emptyList()
    if (instruction is PyWithContextExitInstruction && !instruction.isSuppressingExceptions(context)) return emptyList()
    return instruction.allSucc()
      .filter { it.isReachableWithVersionChecks() }
      .filter { next: Instruction ->
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

  private fun Instruction.isReachableWithVersionChecks(): Boolean {
    return evaluateVersionsForElement(element ?: return true).contains(languageVersion)
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
  return isUnreachableByControlFlow(context) && !isFirstTerminatingStatement(context)
}

/**
 * Determines if the element is unreachable by control flow analysis.
 * If the element does not have corresponding instruction in CFG, searches for the nearest parent that has.
 */
fun PsiElement.isUnreachableByControlFlow(context: TypeEvalContext): Boolean {
  return PyUtil.getParameterizedCachedValue(this, context) { this.isUnreachableByControlFlowNoCache(it) }
}

private fun PsiElement.isUnreachableByControlFlowNoCache(context: TypeEvalContext): Boolean {
  val scope = ScopeUtil.getScopeOwner(this)
  if (scope != null) {
    val flow = ControlFlowCache.getDataFlow(scope, context)
    val instructions = flow.instructions
    val idx = findInstructionNumber(instructions)
    if (idx < 0 || instructions[idx].isAuxiliary()) {
      val parent = this.parent
      return parent != null && parent.isUnreachableByControlFlow(context)
    }
    return flow.isUnreachable(instructions[idx])
  }
  return false
}

/**
 * Like com.intellij.codeInsight.controlflow.ControlFlowUtil.findInstructionNumberByElement,
 * but does not check ProgressManager.checkCanceled().
 * It spends quite some time there.
 */
fun PsiElement.findInstructionNumber(flow: Array<Instruction>): Int {
  for (i in flow.indices) {
    if (this === flow[i].getElement()) {
      return i
    }
  }
  return -1
}

private fun PsiElement.isFirstTerminatingStatement(context: TypeEvalContext): Boolean {
  if (this.isTerminatingStatement(context)) {
    val prevSibling = prevSiblingOfType<PyElement>() ?: return true
    return !prevSibling.isTerminatingStatement(context) && !prevSibling.isUnreachableByControlFlow(context)
  }
  return false
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

private inline fun <reified T: PsiElement> PsiElement.prevSiblingOfType(): T? {
  return PsiTreeUtil.getPrevSiblingOfType(this, T::class.java)
}
