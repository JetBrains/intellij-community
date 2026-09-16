/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.expandTupleTypeParameters

object PyCollectionTypeUtil {

  private const val MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */

  @JvmStatic
  fun getListLiteralType(expression: PyListLiteralExpression, context: TypeEvalContext): PyType? {
    return getSequenceLiteralType(expression, context)
  }

  @JvmStatic
  fun getSetLiteralType(expression: PySetLiteralExpression, context: TypeEvalContext): PyType? {
    return getSequenceLiteralType(expression, context)
  }

  @JvmStatic
  fun getTupleLiteralType(expression: PyTupleExpression, context: TypeEvalContext): PyType? {
    return getSequenceLiteralType(expression, context)
  }

  private fun getSequenceLiteralType(expression: PySequenceExpression, context: TypeEvalContext): PyType? {
    val genericType = findGenericDefinitionTypeOfSequence(expression, context) ?: return PyAnyType.unknown
    val substitutions = PyTypeInferenceCspFactory.unifySequenceExpression(expression, context)
    val concreteType = PyTypeChecker.substitute(genericType, substitutions, context)
    return concreteType
  }

  internal fun getSequenceSubstitutionsFallback(sequence: PySequenceExpression, context: TypeEvalContext) : GenericSubstitutions {
    val genericType = findGenericDefinitionTypeOfSequence(sequence, context) ?: return GenericSubstitutions()
    when (sequence) {
      is PyListLiteralExpression,
      is PySetLiteralExpression -> {
        val typeVar = genericType.typeArguments.firstOrNull() as? PyTypeParameterType ?: return GenericSubstitutions()
        val typeArgument = getListOrSetIteratedValueType(sequence, context)
        return GenericSubstitutions(mapOf(typeVar to typeArgument))
      }
      is PyTupleExpression -> {
        val typeArgs = getTupleElementTypes(sequence, context)
        val typeParams = genericType.typeArguments.filterIsInstance<PyTypeParameterType>()
        val typeParamsMap = typeParams.zip(typeArgs).toMap()
        return GenericSubstitutions(typeParamsMap)
      }
      else -> return GenericSubstitutions()
    }
  }

  internal fun findGenericDefinitionTypeOfSequence(sequence: PySequenceExpression, context: TypeEvalContext): PyClassType? {
    val className = when (sequence) {
      is PyListLiteralExpression -> PyNames.FQN.LIST
      is PySetLiteralExpression -> PyNames.FQN.SET
      is PyTupleExpression -> PyNames.FQN.TUPLE
      else -> return null
    }
    val pyClass = PyBuiltinCache.getInstance(sequence).getClass(className) ?: return null
    val genericDefinitionType = PyTypeChecker.findGenericDefinitionType(pyClass, context) ?: return null

    if (sequence is PyTupleExpression) {
      val flattenedElemsCount = flattenSequenceElements(sequence).size
      if (flattenedElemsCount == 0) {
        return PyTupleType.create(sequence, emptyList())
      }
      val expandedTypeParameters = expandTupleTypeParameters(genericDefinitionType.typeArguments, flattenedElemsCount)
                                   ?: genericDefinitionType.typeArguments
      return PyTupleType.create(sequence, expandedTypeParameters)
    }

    return genericDefinitionType
  }

  private fun getListOrSetIteratedValueType(sequence: PySequenceExpression, context: TypeEvalContext): PyType? {
    val elementTypes = getTupleElementTypes(sequence, context)
    val iteratedItemType = PyTupleType.create(sequence, elementTypes)?.iteratedItemType ?: return PyAnyType.unknown
    if (iteratedItemType is PyNeverType) return PyAnyType.unknown
    // widen since used in invariant list/set
    val analyzedElementsType = PyTypeUtil.widenLiteralAndNumeric(iteratedItemType)
    return if (sequence.elements.size > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
      PyUnionType.createWeakType(analyzedElementsType)
    }
    else {
      analyzedElementsType
    }
  }

  private fun getTupleElementTypes(sequence: PySequenceExpression, context: TypeEvalContext): List<PyType?> {
    val flattenedElements = flattenSequenceElements(sequence).take(MAX_ANALYZED_ELEMENTS_OF_LITERALS)
    val elementTypes = flattenedElements.map { context.getNarrowedType(it) }
    return elementTypes
  }

  internal fun flattenSequenceElements(expression: PySequenceExpression): List<PyExpression> {
    val result = mutableListOf<PyExpression>()
    collectFlattenedElements(expression.elements, result)
    return result
  }

  private fun collectFlattenedElements(elements: Array<out PyExpression>, result: MutableList<PyExpression>) {
    for (element in elements) {
      val starOperand = (element as? PyStarExpression)?.expression
      if (starOperand != null) {
        val unwrappedStarOperand = PyPsiUtils.flattenParens(starOperand)
        when (unwrappedStarOperand) {
          // `(1, *(2, 3))` and `(1, *[2, 3])` splice their elements in directly.
          is PyTupleExpression -> collectFlattenedElements(unwrappedStarOperand.elements, result)
          is PyListLiteralExpression -> collectFlattenedElements(unwrappedStarOperand.elements, result)
          // Any other starred operand cannot be flattened syntactically; keep the star expression as-is.
          else -> result.add(element)
        }
      }
      else {
        result.add(element)
      }
    }
  }

  @JvmStatic
  fun getDictLiteralType(expression: PyDictLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("dict") ?: return PyAnyType.unknown
    val (keyType, valueType) = getDictLiteralElementTypes(expression, context)
    return PyCollectionTypeImpl(cls, false, listOf(keyType, valueType))
  }

  @JvmStatic
  fun getTypedDictFieldsFromDictLiteral(
    sequence: PyDictLiteralExpression,
    context: TypeEvalContext,
  ): Map<String, Pair<PyExpression?, PyType?>>? {
    val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()

    sequence.elements.forEach { element ->
      val elementType = context.getType(element)
      val (keyType, valueType) = getKeyValueType(elementType)

      if (!(keyType is PyClassType && PyNames.FQN.STR == keyType.classQName)) {
        return null
      }
      val keyString: String? = if (keyType is PyLiteralType) {
        keyType.stringValue
      }
      else {
        val keyExpression = element.key
        if (keyExpression is PyStringLiteralExpression) keyExpression.stringValue else null
      }
      if (keyString == null) {
        return null
      }
      strKeysToValueTypes[keyString] = Pair(element.value, PyTypeUtil.widenLiteralAndNumeric(valueType))
    }

    return strKeysToValueTypes
  }

  private fun getDictLiteralElementTypes(sequence: PyDictLiteralExpression, context: TypeEvalContext): Pair<PyType?, PyType?> {
    val elements = sequence.elements
    val keyTypes = mutableListOf<PyType?>()
    val valueTypes = mutableListOf<PyType?>()

    elements
      .take(MAX_ANALYZED_ELEMENTS_OF_LITERALS)
      .forEach {
        val type = context.getType(it)
        val (keyType, valueType) = getKeyValueType(type)
        keyTypes.add(PyTypeUtil.widenLiteralAndNumeric(keyType))
        valueTypes.add(PyTypeUtil.widenLiteralAndNumeric(valueType))
      }

    if (elements.size > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
      keyTypes.add(PyAnyType.unknown)
      valueTypes.add(PyAnyType.unknown)
    }

    return Pair(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
  }

  private fun getKeyValueType(sequenceElementType: PyType?): Pair<PyType?, PyType?> {
    if (sequenceElementType is PyTupleType) {
      if (sequenceElementType.isHomogeneous) {
        val iteratedItemType = sequenceElementType.iteratedItemType
        return iteratedItemType to iteratedItemType
      }
      val tupleElementTypes = sequenceElementType.elementTypes
      if (tupleElementTypes.size == 2) {
        return tupleElementTypes[0] to tupleElementTypes[1]
      }
    }
    return null to null
  }
}
