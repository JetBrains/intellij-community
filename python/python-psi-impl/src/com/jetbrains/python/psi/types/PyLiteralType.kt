// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext


/**
 * Represents literal type introduced in PEP 586.
 */
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
    fun fromLiteralParameter(expression: PyExpression, context: TypeEvalContext): PyType? =
      when (expression) {
        is PyTupleExpression -> {
          val elements = expression.elements
          val classes = elements.mapNotNull { toLiteralType(it, context, true) }
          if (elements.size == classes.size) PyUnionType.union(classes) else null
        }
        else -> toLiteralType(expression, context, true)
      }

    /**
     * Tries to construct literal type or collection of literal types for a value that could be downcasted to `typing.Literal[...] type
     * or its collection.
     */
    private fun fromLiteralValue(expression: PyExpression, context: TypeEvalContext): PyType? {
      val value =
        when (expression) {
          is PyKeywordArgument -> expression.valueExpression
          is PyParenthesizedExpression -> PyPsiUtils.flattenParens(expression)
          else -> expression
        } ?: return null
      return when (value) {
        is PyDictLiteralExpression -> {
          val keyType = PyUnionType.union(value.elements.map { fromLiteralValue(it.key, context) })
          val valueType = PyUnionType.union(value.elements.mapNotNull { type -> type.value?.let { fromLiteralValue(it, context) } })
          PyCollectionTypeImpl.createTypeByQName(value, "dict", false, listOf(keyType, valueType))
        }
        is PyTupleExpression -> {
          val elementTypes = value.elements.map { fromLiteralValue(it, context) }
          PyTupleType.create(value, elementTypes)
        }
        is PySetLiteralExpression -> {
          val elementType = PyUnionType.union(value.elements.map { fromLiteralValue(it, context) })
          PyCollectionTypeImpl.createTypeByQName(value, "set", false, listOf(elementType))
        }
        is PyListLiteralExpression -> {
          val elementType = PyUnionType.union(value.elements.map { fromLiteralValue(it, context) })
          PyCollectionTypeImpl.createTypeByQName(value, "list", false, listOf(elementType))
        }
        else -> toLiteralType(value, context, false) ?: context.getType(value)
      }
    }

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
    fun promoteToLiteral(expression: PyExpression,
                         expected: PyType?,
                         context: TypeEvalContext,
                         substitutions: PyTypeChecker.GenericSubstitutions?): PyType? {
      if (expected is PyTypedDictType) {
        return null
      }
      val substitution = if (substitutions != null) PyTypeChecker.substitute(expected, substitutions, context) else expected
      val substitutionOrBound = if (substitution is PyTypeVarType) substitution.bound else substitution
      if (containsLiteral(substitutionOrBound)) {
        return fromLiteralValue(expression, context)
      }
      return null
    }

    private fun containsLiteral(type: PyType?): Boolean {
      return type is PyLiteralType || type is PyLiteralStringType ||
             type is PyUnionType && type.members.any { containsLiteral(it) } ||
             type is PyCollectionType && type.elementTypes.any { containsLiteral(it) }
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
          .multiResolveTopPriority(expression, PyResolveContext.defaultContext(context))
          .asSequence()
          .filterIsInstance<PyTargetExpression>()
          .mapNotNull { ScopeUtil.getScopeOwner(it) as? PyClass }
          .firstOrNull { owner -> PyStdlibTypeProvider.isEnum(owner, context) }
          ?.let {
            val type = context.getType(it)
            return if (type is PyInstantiableType<*>) type.toInstance() else type
          }
      }

      if (expression is PyConditionalExpression) {
        return PyUnionType.union(listOf(expression.truePart, expression.falsePart).map { expr ->
          expr?.let { classOfAcceptableLiteral(expr, context, index)?.let { cls -> PyLiteralType(cls, expr) } }
        })
      }

      if (expression is PyStringLiteralExpression && expression.isInterpolated) {
        val allLiteralStringFragments = expression.stringElements
          .filterIsInstance<PyFormattedStringElement>()
          .flatMap { it.fragments }
          .mapNotNull { it.expression }
          .map { context.getType(it) }
          .all { it is PyLiteralStringType }
        if (allLiteralStringFragments) {
          return PyLiteralStringType.create(expression)
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
