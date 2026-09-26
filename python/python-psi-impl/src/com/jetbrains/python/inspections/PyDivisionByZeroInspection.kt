// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyGlobalStatement
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyNonlocalStatement
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyInstantiableType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.TypeEvalContext

private const val FRACTION_QN = "fractions.Fraction"

private const val DECIMAL_QN = "decimal.Decimal"

private const val FRACTION_DENOMINATOR = "denominator"

// The following functions are known to raise an error on a zero divisor:

private val KNOWN_TRUEDIV_FUNCTIONS = setOf(
  "${PyNames.FQN.INT}.__truediv__",
  "${PyNames.FQN.FLOAT}.__truediv__",
  "${PyNames.FQN.COMPLEX}.__truediv__",
  "$FRACTION_QN.__truediv__",
  "$DECIMAL_QN.__truediv__"
)

private val KNOWN_FLOORDIV_FUNCTIONS = setOf(
  "${PyNames.FQN.INT}.__floordiv__",
  "${PyNames.FQN.FLOAT}.__floordiv__",
  "$FRACTION_QN.__floordiv__",
  "$DECIMAL_QN.__floordiv__"
)

private val KNOWN_MOD_FUNCTIONS = setOf(
  "${PyNames.FQN.INT}.__mod__",
  "${PyNames.FQN.FLOAT}.__mod__",
  "$FRACTION_QN.__mod__",
  "$DECIMAL_QN.__mod__"
)

private val KNOWN_DIVMOD_FUNCTIONS = setOf(
  "${PyNames.FQN.INT}.__divmod__",
  "${PyNames.FQN.FLOAT}.__divmod__",
  "$FRACTION_QN.__divmod__",
  "$DECIMAL_QN.__divmod__"
)

/**
 * One flavor of division (`/`, `//`, `%`, `divmod`, or their in-place variants).
 *
 * Python operators and calls get mapped to `DivisionOperation`. We try to resolve the special
 * [methods] that Python's runtime calls when performing this division operation.
 * If resolution lands on one of the implementations known to raise an error on a zero divisor
 * ([knownFunctions]), we report a warning.
 *
 * @param methods names of methods Python calls for this operation, in resolution order
 * @param knownFunctions qualified names of [methods] implementations that raise an error on a zero divisor
 */
private enum class DivisionOperation(val methods: List<String>, val knownFunctions: Set<String>) {
  TRUEDIV(listOf("__truediv__"), KNOWN_TRUEDIV_FUNCTIONS),
  FLOORDIV(listOf("__floordiv__"), KNOWN_FLOORDIV_FUNCTIONS),
  MOD(listOf("__mod__"), KNOWN_MOD_FUNCTIONS),
  DIVMOD(listOf("__divmod__"), KNOWN_DIVMOD_FUNCTIONS),
  ITRUEDIV(listOf("__itruediv__") + TRUEDIV.methods, KNOWN_TRUEDIV_FUNCTIONS),
  IFLOORDIV(listOf("__ifloordiv__") + FLOORDIV.methods, KNOWN_FLOORDIV_FUNCTIONS),
  IMOD(listOf("__imod__") + MOD.methods, KNOWN_MOD_FUNCTIONS);

  companion object {
    val OPERATOR_TO_OPERATION = mapOf(
      PyTokenTypes.DIV to TRUEDIV,
      PyTokenTypes.DIVEQ to ITRUEDIV,
      PyTokenTypes.FLOORDIV to FLOORDIV,
      PyTokenTypes.FLOORDIVEQ to IFLOORDIV,
      PyTokenTypes.PERC to MOD,
      PyTokenTypes.PERCEQ to IMOD
    )

    val CALLEE_TO_OPERATION = mapOf(
      "builtins.${PyNames.DIVMOD}" to DIVMOD,
      // typeshed defines the `operator` module functions in `_operator`, re-exported by `operator`
      "operator.truediv" to TRUEDIV, "_operator.truediv" to TRUEDIV,
      "operator.itruediv" to ITRUEDIV, "_operator.itruediv" to ITRUEDIV,
      "operator.floordiv" to FLOORDIV, "_operator.floordiv" to FLOORDIV,
      "operator.ifloordiv" to IFLOORDIV, "_operator.ifloordiv" to IFLOORDIV,
      "operator.mod" to MOD, "_operator.mod" to MOD,
      "operator.imod" to IMOD, "_operator.imod" to IMOD
    )
  }
}

class PyDivisionByZeroInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      val operation = DivisionOperation.OPERATOR_TO_OPERATION[node.operator] ?: return
      val dividend = node.leftExpression ?: return
      if (isZero(node.rightExpression) && operation.raisesOnZeroDivisor(dividend)) {
        registerProblem(node, PyPsiBundle.message("INSP.division.by.zero"))
      }
    }

    override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
      val operator = node.operation?.node?.elementType as? PyElementType ?: return
      val operation = DivisionOperation.OPERATOR_TO_OPERATION[operator] ?: return
      if (isZero(node.value) && operation.raisesOnZeroDivisor(node.target)) {
        registerProblem(node, PyPsiBundle.message("INSP.division.by.zero"))
      }
    }

    override fun visitPyCallExpression(node: PyCallExpression) {
      val arguments = node.arguments
      if (arguments.size != 2 || arguments.any { it is PyStarArgument }) return
      val (first, second) = arguments
      // `divmod` and the `operator` functions take positional-only arguments, `Fraction` also takes keyword arguments
      val isPositional = first !is PyKeywordArgument && second !is PyKeywordArgument
      val divisor = if (isPositional) second else node.getKeywordArgument(FRACTION_DENOMINATOR)
      // Check the divisor before the callee resolution, because the resolution is more expensive
      if (!isZero(divisor)) return

      val callees = node.multiResolveCalleeFunction(resolveContext)
        .filterIsInstance<PyFunction>()
        .mapNotNull { PyUtil.turnConstructorIntoClass(it)?.qualifiedName ?: it.qualifiedName }

      val isReported = when {
        FRACTION_QN in callees -> true
        !isPositional -> false
        else -> callees.firstNotNullOfOrNull { DivisionOperation.CALLEE_TO_OPERATION[it] }?.raisesOnZeroDivisor(first) == true
      }
      if (isReported) {
        registerProblem(node, PyPsiBundle.message("INSP.division.by.zero"))
      }
    }

    private tailrec fun isZero(expression: PyExpression?): Boolean {
      val unwrapped = PyPsiUtils.flattenParens(expression) ?: return false
      if (unwrapped is PyPrefixExpression && (unwrapped.operator == PyTokenTypes.PLUS || unwrapped.operator == PyTokenTypes.MINUS)) {
        return isZero(unwrapped.operand)
      }
      if (unwrapped is PyNumericLiteralExpression) {
        return unwrapped.bigDecimalValue.signum() == 0
      }
      val literalType = myTypeEvalContext.getType(unwrapped) as? PyLiteralType ?: return false
      if (literalType.intValue?.signum() != 0 && literalType.boolValue != false) return false
      return unwrapped !is PyReferenceExpression || isNotReboundInOtherScope(unwrapped)
    }

    /**
     * Checks that [reference] resolves only to variables of its own scope, and that no nested scope
     * rebinds them with `global` or `nonlocal`. Otherwise, the inferred literal type is not reliable.
     */
    private fun isNotReboundInOtherScope(reference: PyReferenceExpression): Boolean {
      if (reference.isQualified) return false
      val name = reference.referencedName ?: return false
      val owner = ScopeUtil.getScopeOwner(reference) ?: return false
      val resolved = reference.getReference(resolveContext).multiResolve(false).map { it.element }
      if (resolved.isEmpty() || resolved.any { (it !is PyTargetExpression && it !is PyNamedParameter) || ScopeUtil.getScopeOwner(it) != owner }) {
        return false
      }
      val rebindings = PsiTreeUtil.findChildrenOfAnyType(owner, PyGlobalStatement::class.java, PyNonlocalStatement::class.java)
        .flatMap { (it as? PyGlobalStatement)?.globals?.asList() ?: (it as PyNonlocalStatement).variables.asList() }
      return rebindings.none { it.name == name }
    }

    /**
     * Checks that every union member of the [dividend] type resolves [DivisionOperation.methods]
     * to one of [DivisionOperation.knownFunctions].
     */
    private fun DivisionOperation.raisesOnZeroDivisor(dividend: PyExpression): Boolean {
      val dividendType = myTypeEvalContext.getType(dividend) ?: return false
      return dividendType.asUnionSequence().all { it != null && resolvesToKnownFunction(it, dividend) }
    }

    private fun DivisionOperation.resolvesToKnownFunction(type: PyType, dividend: PyExpression): Boolean {
      // A class object does not support the division operators of its instances
      if (type is PyInstantiableType<*> && type.isDefinition) return false
      val resolved = methods.asSequence()
        .mapNotNull { type.resolveMember(it, dividend, AccessDirection.READ, resolveContext) }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
        .mapNotNull { (it.element as? PyFunction)?.qualifiedName }
      return resolved.any { it in knownFunctions }
    }
  }
}
