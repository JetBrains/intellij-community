// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import org.jetbrains.annotations.ApiStatus


/**
 * Represents literal type introduced in PEP 586.
 */
@ApiStatus.NonExtendable
class PyLiteralType private constructor(cls: PyClass, val expression: PyExpression) : PyClassTypeImpl(cls, false) {

  override fun getName(): String = "Literal[${expression.text}]"

  override fun toString(): String = "PyLiteralType: ${expression.text}"

  override fun equals(other: Any?): Boolean {
    return this === other || javaClass == other?.javaClass && match(this, other as PyLiteralType)
  }

  override fun hashCode(): Int = 31 * pyClass.hashCode()

  companion object {
    /**
     * Tries to construct literal type for index passed to `typing.Literal[...]`
     */
    fun fromLiteralParameter(expression: PyExpression, context: TypeEvalContext): PyType? = newInstance(expression, context, true)

    /**
     * Tries to construct literal type for a value that could be considered as literal and downcasted to `typing.Literal[...]` type.
     */
    fun fromLiteralValue(expression: PyExpression, context: TypeEvalContext): PyType? = newInstance(expression, context, false)

    /**
     * [actual] matches [expected] if it has the same type and its expression evaluates to the same value
     */
    fun match(expected: PyLiteralType, actual: PyLiteralType): Boolean {
      return expected.pyClass == actual.pyClass &&
             PyEvaluator.evaluateNoResolve(expected.expression, Any::class.java) ==
             PyEvaluator.evaluateNoResolve(actual.expression, Any::class.java)
    }

    /**
     * If [expected] type is `typing.Literal[...]`,
     * then tries to infer `typing.Literal[...]` for [expression],
     * otherwise returns type inferred by [context].
     */
    fun promoteToLiteral(expression: PyExpression, expected: PyType?, context: TypeEvalContext): PyType? {
      if (PyTypeUtil.toStream(if (expected is PyGenericType) expected.bound else expected).any { it is PyLiteralType }) {
        val value = if (expression is PyKeywordArgument) expression.valueExpression else expression
        if (value != null) {
          val literalType = fromLiteralValue(value, context)
          if (literalType != null) {
            return literalType
          }
        }
      }

      return context.getType(expression)
    }

    private fun newInstance(expression: PyExpression, context: TypeEvalContext, index: Boolean): PyType? {
      return when (expression) {
        is PyTupleExpression -> {
          val elements = expression.elements
          val classes = elements.mapNotNull { toLiteralType(it, context, index) }
          if (elements.size == classes.size) PyUnionType.union(classes) else null
        }
        else -> toLiteralType(expression, context, index)
      }
    }

    private fun toLiteralType(expression: PyExpression, context: TypeEvalContext, index: Boolean): PyType? {
      if (expression is PyNoneLiteralExpression && !expression.isEllipsis ||
          expression is PyReferenceExpression &&
          expression.name == PyNames.NONE &&
          LanguageLevel.forElement(expression).isPython2) return PyNoneType.INSTANCE

      if (index && (expression is PyReferenceExpression || expression is PySubscriptionExpression)) {
        val subLiteralType = Ref.deref(PyTypingTypeProvider.getType(expression, context))
        if (PyTypeUtil.toStream(subLiteralType).all { it is PyLiteralType }) return subLiteralType
      }

      if (expression is PyReferenceExpression && expression.isQualified) {
        PyUtil
          .multiResolveTopPriority(expression, PyResolveContext.noImplicits().withTypeEvalContext(context))
          .asSequence()
          .filterIsInstance<PyTargetExpression>()
          .mapNotNull { ScopeUtil.getScopeOwner(it) as? PyClass }
          .firstOrNull { owner -> owner.getAncestorTypes(context).any { it?.classQName == "enum.Enum" } }
          ?.let {
            val type = context.getType(it)
            return if (type is PyInstantiableType<*>) type.toInstance() else type
          }
      }

      return classOfAcceptableLiteral(expression, context, index)?.let { PyLiteralType(it, expression) }
    }

    private fun classOfAcceptableLiteral(expression: PyExpression, context: TypeEvalContext, index: Boolean): PyClass? {
      return when {
        expression is PyNumericLiteralExpression -> if (expression.isIntegerLiteral) getPyClass(expression, context) else null

        expression is PyStringLiteralExpression ->
          if (isAcceptableStringLiteral(expression, index)) getPyClass(expression, context) else null

        expression is PyLiteralExpression -> getPyClass(expression, context)

        expression is PyPrefixExpression && expression.operator == PyTokenTypes.MINUS -> {
          val operand = expression.operand
          if (operand is PyNumericLiteralExpression && operand.isIntegerLiteral) getPyClass(operand, context) else null
        }

        expression is PyReferenceExpression &&
        expression.name.let { it == PyNames.TRUE || it == PyNames.FALSE } &&
        LanguageLevel.forElement(expression).isPython2 -> getPyClass(expression, context)

        else -> null
      }
    }

    private fun getPyClass(expression: PyExpression, context: TypeEvalContext) = (context.getType(expression) as? PyClassType)?.pyClass

    private fun isAcceptableStringLiteral(expression: PyStringLiteralExpression, index: Boolean): Boolean {
      val singleElement = expression.stringElements.singleOrNull() ?: return false
      return if (!index && singleElement is PyFormattedStringElement) singleElement.fragments.isEmpty()
      else singleElement is PyPlainStringElement
    }
  }
}