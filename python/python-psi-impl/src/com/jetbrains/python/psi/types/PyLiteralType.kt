// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
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

      return classOfAcceptableLiteral(expression, context)?.let { PyLiteralType(it, expression) }
    }

    private fun classOfAcceptableLiteral(expression: PyExpression, context: TypeEvalContext): PyClass? {
      return when {
        expression is PyNumericLiteralExpression -> if (expression.isIntegerLiteral) getPyClass(expression, context) else null

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
  }
}