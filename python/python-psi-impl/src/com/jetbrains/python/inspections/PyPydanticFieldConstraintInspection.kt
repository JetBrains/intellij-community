package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.PyDataclassNames
import com.jetbrains.python.codeInsight.isPydanticModel
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.Nls
import java.math.BigDecimal
import java.math.BigInteger

class PyPydanticFieldConstraintInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyCallExpression(node: PyCallExpression) {
      super.visitPyCallExpression(node)

      val pyClass = PyCallExpressionHelper.resolveCalleeClass(node) ?: return
      if (!isPydanticModel(pyClass, myTypeEvalContext)) return

      for (argument in node.arguments) {
        if (argument !is PyKeywordArgument) continue
        val argName = argument.keyword ?: continue
        val value = argument.valueExpression ?: continue
        for (fieldCall in findFieldCallsByNameOrAlias(pyClass, argName)) {
          checkConstraints(value, argName, fieldCall)
        }
      }
    }

    private fun findFieldCallsByNameOrAlias(pyClass: PyClass, argName: String): List<PyCallExpression> {
      pyClass.findClassAttribute(argName, true, myTypeEvalContext)?.let { attribute ->
        val calls = findPydanticFieldCalls(attribute, myTypeEvalContext)
        if (calls.isNotEmpty()) return calls
      }
      for (attribute in pyClass.getClassAttributesInherited(myTypeEvalContext).asReversed()) {
        val calls = findPydanticFieldCalls(attribute, myTypeEvalContext)
        if (calls.any { matchesAlias(it, argName) }) return calls
      }
      return emptyList()
    }

    private fun checkConstraints(value: PyExpression, fieldName: String, fieldCall: PyCallExpression) {
      val numericValue = evaluateAsBigDecimal(value)
      if (numericValue != null) {
        val gt = evaluateAsBigDecimal(fieldCall.getKeywordArgument("gt"))
        if (gt != null && numericValue <= gt) {
          registerValueConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.greater"), gt)
        }
        val ge = evaluateAsBigDecimal(fieldCall.getKeywordArgument("ge"))
        if (ge != null && numericValue < ge) {
          registerValueConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.greater.or.equal"), ge)
        }
        val lt = evaluateAsBigDecimal(fieldCall.getKeywordArgument("lt"))
        if (lt != null && numericValue >= lt) {
          registerValueConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.less"), lt)
        }
        val le = evaluateAsBigDecimal(fieldCall.getKeywordArgument("le"))
        if (le != null && numericValue > le) {
          registerValueConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.less.or.equal"), le)
        }
      }

      val stringValue = evaluateAsString(value)
      if (stringValue != null) {
        val length = stringValue.length.toBigInteger()
        val minLength = evaluateAsBigInteger(fieldCall.getKeywordArgument("min_length"))
        if (minLength != null && length < minLength) {
          registerStringLengthConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.at.least"), minLength)
        }
        val maxLength = evaluateAsBigInteger(fieldCall.getKeywordArgument("max_length"))
        if (maxLength != null && length > maxLength) {
          registerStringLengthConstraint(value, fieldName, PyPsiBundle.message("INSP.pydantic.field.comparison.at.most"), maxLength)
        }
      }
    }

    private fun registerValueConstraint(value: PyExpression, fieldName: String, comparison: @Nls String, bound: BigDecimal) {
      registerProblem(value,
                      PyPsiBundle.problemMessage("INSP.pydantic.field.value.constraint", fieldName, comparison, bound.toPlainString()))
    }

    private fun registerStringLengthConstraint(value: PyExpression, fieldName: String, comparison: @Nls String, bound: BigInteger) {
      registerProblem(value,
                      PyPsiBundle.problemMessage("INSP.pydantic.field.string.length.constraint", fieldName, comparison, bound.toString()))
    }

    private fun evaluateAsBigDecimal(expression: PyExpression?): BigDecimal? {
      var current: PyExpression? = expression
      var negate = false
      val visited = mutableSetOf<PyExpression>()
      while (current != null) {
        val unwrapped = PyPsiUtils.flattenParens(current) ?: return null
        if (!visited.add(unwrapped)) return null
        when (unwrapped) {
          is PyNumericLiteralExpression -> {
            return if (negate) unwrapped.bigDecimalValue.negate() else unwrapped.bigDecimalValue
          }
          is PyPrefixExpression if unwrapped.operator == PyTokenTypes.MINUS -> {
            negate = !negate
            current = unwrapped.operand
          }
          is PyReferenceExpression -> {
            current = resolveToSingleAssignedValue(unwrapped)
          }
          else -> return null
        }
      }
      return null
    }

    private fun evaluateAsBigInteger(expression: PyExpression?): BigInteger? {
      val value = evaluateAsBigDecimal(expression) ?: return null
      if (value.scale() > 0) return null
      return value.toBigInteger()
    }

    private fun evaluateAsString(expression: PyExpression?): String? {
      var current: PyExpression? = expression
      val visited = mutableSetOf<PyExpression>()
      while (current != null) {
        val unwrapped = PyPsiUtils.flattenParens(current) ?: return null
        if (!visited.add(unwrapped)) return null
        when (unwrapped) {
          is PyStringLiteralExpression -> return unwrapped.stringValue
          is PyReferenceExpression -> {
            current = resolveToSingleAssignedValue(unwrapped)
          }
          else -> return null
        }
      }
      return null
    }

    private fun resolveToSingleAssignedValue(reference: PyReferenceExpression): PyExpression? {
      if (reference.isQualified) return null
      val results = reference.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).multiResolve(false)
      if (results.size != 1) return null
      val target = results[0].element as? PyTargetExpression ?: return null
      if (!myTypeEvalContext.maySwitchToAST(target)) return null
      return target.findAssignedValue()
    }
  }
}

private fun findPydanticFieldCalls(field: PyTargetExpression, context: TypeEvalContext): List<PyCallExpression> {
  if (!context.maySwitchToAST(field)) return emptyList()
  val result = mutableListOf<PyCallExpression>()
  val assignedValue = field.findAssignedValue()
  if (assignedValue is PyCallExpression && isPydanticFieldCall(assignedValue)) {
    result.add(assignedValue)
  }
  result.addAll(findPydanticFieldCallsInAnnotated(field))
  return result
}

private fun findPydanticFieldCallsInAnnotated(field: PyTargetExpression): List<PyCallExpression> {
  val annotation = field.annotation?.value as? PySubscriptionExpression ?: return emptyList()
  val operand = annotation.operand as? PyReferenceExpression ?: return emptyList()
  val resolvedNames = PyResolveUtil.resolveImportedElementQNameLocally(operand).map { it.toString() }
  if (resolvedNames.none { it == PyTypingTypeProvider.ANNOTATED || it == PyTypingTypeProvider.ANNOTATED_EXT }) {
    return emptyList()
  }
  val index = PyPsiUtils.flattenParens(annotation.indexExpression) as? PyTupleExpression ?: return emptyList()
  return index.elements
    .drop(1) // the first element is the type (e.g., str, int)
    .mapNotNull { PyPsiUtils.flattenParens(it) as? PyCallExpression }
    .filter { isPydanticFieldCall(it) }
}

private fun matchesAlias(fieldCall: PyCallExpression, argName: String): Boolean {
  val alias = PyPsiUtils.flattenParens(fieldCall.getKeywordArgument(PyDataclassNames.Pydantic.ALIAS))
  val aliasValue = (alias as? PyStringLiteralExpression)?.stringValue
  if (aliasValue == argName) return true

  val validationAliases = PyDataclassFieldStubImpl.extractValidationAliases(
    PyPsiUtils.flattenParens(fieldCall.getKeywordArgument(PyDataclassNames.Pydantic.VALIDATION_ALIAS))
  )
  return argName in validationAliases
}

private fun isPydanticFieldCall(call: PyCallExpression): Boolean {
  val callee = call.callee as? PyReferenceExpression ?: return false
  return PyResolveUtil.resolveImportedElementQNameLocally(callee).any {
    it.toString() in PyDataclassNames.Pydantic.PYDANTIC_FIELD_QUALIFIED_NAMES
  }
}
