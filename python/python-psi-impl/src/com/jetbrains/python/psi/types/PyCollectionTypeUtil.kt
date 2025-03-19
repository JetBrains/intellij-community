/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache

object PyCollectionTypeUtil {

  private const val MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */

  @JvmStatic
  fun getListLiteralType(expression: PyListLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("list") ?: return null
    return PyCollectionTypeImpl(cls, false, listOf(getListOrSetIteratedValueType(expression, context)))
  }

  @JvmStatic
  fun getSetLiteralType(expression: PySetLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("set") ?: return null
    return PyCollectionTypeImpl(cls, false, listOf(getListOrSetIteratedValueType(expression, context)))
  }

  private fun getListOrSetIteratedValueType(sequence: PySequenceExpression, context: TypeEvalContext): PyType? {
    val elements = sequence.elements
    val analyzedElementsType = PyUnionType.union(
      elements.take(MAX_ANALYZED_ELEMENTS_OF_LITERALS).map { PyLiteralType.upcastLiteralToClass(context.getType(it)) }
    )
    return if (elements.size > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
      PyUnionType.createWeakType(analyzedElementsType)
    }
    else {
      analyzedElementsType
    }
  }

  @JvmStatic
  fun getDictLiteralType(expression: PyDictLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("dict") ?: return null
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

      if (!(keyType is PyClassType && PyNames.TYPE_STR == keyType.classQName)) {
        return null
      }
      val keyExpression = if (keyType is PyLiteralType) {
        keyType.expression
      }
      else {
        element.key
      }
      if (keyExpression !is PyStringLiteralExpression) {
        return null
      }
      strKeysToValueTypes[keyExpression.stringValue] = Pair(element.value, PyLiteralType.upcastLiteralToClass(valueType))
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
        keyTypes.add(PyLiteralType.upcastLiteralToClass(keyType))
        valueTypes.add(PyLiteralType.upcastLiteralToClass(valueType))
      }

    if (elements.size > MAX_ANALYZED_ELEMENTS_OF_LITERALS) {
      keyTypes.add(null)
      valueTypes.add(null)
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
