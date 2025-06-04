// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal


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
  
  override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyLiteralType(this)
    }
    return visitor.visitPyClassType(this)  
  }

  companion object {
    /**
     * Tries to construct literal type for index passed to `typing.Literal[...]`
     */
    fun fromLiteralParameter(expression: PyExpression, context: TypeEvalContext): PyType? =
      when (expression) {
        is PyTupleExpression -> {
          val elements = expression.elements
          val classes = elements.mapNotNull { createFromLiteralParameter(it, context) }
          if (elements.size == classes.size) PyUnionType.union(classes) else null
        }
        else -> createFromLiteralParameter(expression, context)
      }

    @JvmStatic
    fun enumMember(enumClass: PyClass, memberName: String): PyLiteralType {
      val expression = PyElementGenerator.getInstance(enumClass.getProject())
        .createExpressionFromText(LanguageLevel.forElement(enumClass), "${enumClass.name}.$memberName")
      assert(expression is PyReferenceExpression)
      return PyLiteralType(enumClass, expression)
    }

    @Internal
    @JvmStatic
    fun upcastLiteralToClass(type: PyType?): PyType? {
      return when (type) {
        is PyUnionType -> type.map(::upcastLiteralToClass)
        is PyLiteralStringType -> PyClassTypeImpl(type.cls, false)
        is PyLiteralType -> PyClassTypeImpl(type.pyClass, false)
        else -> type
      }
    }

    private fun promoteToType(
      expectedType: PyType?,
      expression: PyExpression,
      context: TypeEvalContext,
      inferLiteralTypes: Boolean,
    ): PyType? {
      val value = PyUtil.peelArgument(expression) ?: return null
      return when (value) {
        is PyDictLiteralExpression -> {
          promoteDictLiteral(expectedType, value, context, inferLiteralTypes)
        }
        is PyTupleExpression -> {
          promoteTuple(value, context, inferLiteralTypes)
        }
        is PySetLiteralExpression -> {
          promoteSetLiteral(expectedType, value, context, inferLiteralTypes)
        }
        is PyListLiteralExpression -> {
          promoteListLiteral(expectedType, value, context, inferLiteralTypes)
        }
        else -> {
          val type = if (inferLiteralTypes) getLiteralOrLiteralStringType(value, context) else null
          return type ?: context.getType(value)
        }
      }
    }

    private fun promoteDictLiteral(
      expectedType: PyType?,
      dictLiteral: PyDictLiteralExpression,
      context: TypeEvalContext,
      inferLiteralTypes: Boolean,
    ): PyType? {
      if (expectedType is PyTypedDictType) {
        val typeCheckingResult = PyTypedDictType.TypeCheckingResult()
        PyTypedDictType.checkExpression(expectedType, dictLiteral, context, typeCheckingResult)
        if (!typeCheckingResult.hasErrors) {
          return expectedType
        }
      }
      val (expectedKeyType, expectedValueType) = if (expectedType is PyCollectionType && expectedType.classQName == PyNames.DICT) {
        expectedType.elementTypes[0] to expectedType.elementTypes[1]
      }
      else {
        null to null
      }
      val keyType = PyUnionType.union(dictLiteral.elements.map { promoteToType(expectedKeyType, it.key, context, inferLiteralTypes) })
      val valueType = PyUnionType.union(
        dictLiteral.elements.mapNotNull { type -> type.value?.let { promoteToType(expectedValueType, it, context, inferLiteralTypes) } }
      )
      return PyCollectionTypeImpl.createTypeByQName(dictLiteral, PyNames.DICT, false, listOf(keyType, valueType))
    }

    private fun promoteTuple(
      tupleExpression: PyTupleExpression,
      context: TypeEvalContext,
      inferLiteralTypes: Boolean,
    ): PyTupleType? {
      val elementTypes = tupleExpression.elements.map { promoteToType(/*TODO*/null, it, context, inferLiteralTypes) }
      return PyTupleType.create(tupleExpression, elementTypes)
    }

    private fun promoteSetLiteral(
      expectedType: PyType?,
      setLiteral: PySetLiteralExpression,
      context: TypeEvalContext,
      inferLiteralTypes: Boolean,
    ): PyCollectionTypeImpl? {
      val expectedElementType = if (expectedType is PyCollectionType && expectedType.classQName == "set") {
        expectedType.elementTypes.firstOrNull()
      }
      else {
        null
      }
      val elementType = PyUnionType.union(setLiteral.elements.map { promoteToType(expectedElementType, it, context, inferLiteralTypes) })
      return PyCollectionTypeImpl.createTypeByQName(setLiteral, "set", false, listOf(elementType))
    }

    private fun promoteListLiteral(
      expectedType: PyType?,
      value: PyListLiteralExpression,
      context: TypeEvalContext,
      inferLiteralTypes: Boolean,
    ): PyCollectionTypeImpl? {
      val expectedElementType = if (expectedType is PyCollectionType && expectedType.classQName == "list") {
        expectedType.elementTypes.firstOrNull()
      }
      else {
        null
      }
      val elementType = PyUnionType.union(value.elements.map { promoteToType(expectedElementType, it, context, inferLiteralTypes) })
      return PyCollectionTypeImpl.createTypeByQName(value, "list", false, listOf(elementType))
    }

    /**
     * [actual] matches [expected] if it has the same type and its expression evaluates to the same value
     */
    fun match(expected: PyLiteralType, actual: PyLiteralType): Boolean {
      if (expected.pyClass != actual.pyClass) return false
      if (expected.expression is PyReferenceExpression && actual.expression is PyReferenceExpression) {
        return expected.expression.name == actual.expression.name
      }
      return PyEvaluator.evaluateNoResolve(expected.expression, Any::class.java) ==
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
      val substitution = if (substitutions != null) PyTypeChecker.substitute(expected, substitutions, context) else expected
      val substitutionOrBound = if (substitution is PyTypeVarType) PyTypeUtil.getEffectiveBound(substitution) else substitution
      if (substitutionOrBound == null) return null
      return promoteToType(substitutionOrBound, expression, context, containsLiteral(substitutionOrBound))
    }

    private fun containsLiteral(type: PyType?): Boolean {
      return type is PyLiteralType || type is PyLiteralStringType ||
             type is PyUnionType && type.members.any { containsLiteral(it) } ||
             type is PyCollectionType && type.elementTypes.any { containsLiteral(it) }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun isNone(expression: PyExpression): Boolean {
      return expression is PyNoneLiteralExpression && !expression.isEllipsis ||
             expression is PyReferenceExpression &&
             expression.name == PyNames.NONE &&
             LanguageLevel.forElement(expression).isPython2
    }

    private fun createFromLiteralParameter(expression: PyExpression, context: TypeEvalContext): PyType? {
      if (isNone(expression)) return PyNoneType.INSTANCE

      if (expression is PyReferenceExpression || expression is PySubscriptionExpression) {
        val subLiteralType = Ref.deref(PyTypingTypeProvider.getType(expression, context))
        if (PyTypeUtil.toStream(subLiteralType).all { it is PyLiteralType }) return subLiteralType
      }

      return literalType(expression, context, true)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getLiteralType(expression: PyExpression, context: TypeEvalContext): PyType? {
      if (expression is PyConditionalExpression) {
        return PyUnionType.union(
          listOf(expression.truePart, expression.falsePart).map {
            it?.let { getLiteralType(it, context) }
          }
        )
      }
      return literalType(expression, context, false)
    }

    private fun getLiteralOrLiteralStringType(expression: PyExpression, context: TypeEvalContext): PyType? {
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
      return getLiteralType(expression, context)
    }

    private fun literalType(expression: PyExpression, context: TypeEvalContext, index: Boolean): PyLiteralType? {
      if (expression is PyReferenceExpression && expression.isQualified) {
        val type = PyUtil.multiResolveTopPriority(expression, PyResolveContext.defaultContext(context)).firstNotNullOfOrNull {
          PyStdlibTypeProvider.getEnumMemberType(it, context)
        }
        if (type != null) {
          return type
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

        expression is PyPrefixExpression && (expression.operator == PyTokenTypes.PLUS || expression.operator == PyTokenTypes.MINUS) -> {
          val operand = expression.operand
          if (operand is PyNumericLiteralExpression && operand.isIntegerLiteral) getPyClass(operand, context) else null
        }

        PyEvaluator.getBooleanLiteralValue(expression) != null -> getPyClass(expression, context)

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
