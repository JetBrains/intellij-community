/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache

object PyCollectionTypeUtil {

  private const val MAX_ANALYZED_ELEMENTS_OF_LITERALS = 10 /* performance */

  @JvmStatic
  fun getListLiteralType(expression: PyListLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("list") ?: return null
    return createCollectionType(cls, getListOrSetIteratedValueType(expression, context))
  }

  @JvmStatic
  fun getSetLiteralType(expression: PySetLiteralExpression, context: TypeEvalContext): PyType? {
    val cls = PyBuiltinCache.getInstance(expression).getClass("set") ?: return null
    return createCollectionType(cls, getListOrSetIteratedValueType(expression, context))
  }

  private fun getListOrSetIteratedValueType(sequence: PySequenceExpression, context: TypeEvalContext): PyType? {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    val analyzedElementsType = PyUnionType.union(elements.take(maxAnalyzedElements).map { context.getType(it) })
    return if (elements.size > maxAnalyzedElements) {
      PyUnionType.createWeakType(analyzedElementsType)
    }
    else {
      analyzedElementsType
    }
  }

  @JvmStatic
  fun getDictLiteralType(expression: PyDictLiteralExpression, context: TypeEvalContext): PyType? {
    val typedDictType = getTypedDictTypeFromDictLiteral(expression, context)
    if (typedDictType != null) {
      return typedDictType
    }
    val cls = PyBuiltinCache.getInstance(expression).getClass("dict") ?: return null
    val (keyType, valueType) = getDictLiteralElementTypes(expression, context)
    return createCollectionType(cls, keyType, valueType)
  }

  private fun getTypedDictTypeFromDictLiteral(sequence: PyDictLiteralExpression, context: TypeEvalContext): PyTypedDictType? {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    if (elements.size > maxAnalyzedElements) {
      return null
    }

    val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()

    elements
      .take(maxAnalyzedElements)
      .forEach { element ->
        val elementType = context.getType(element)
        val (keyType, valueType) = getKeyValueType(elementType) ?: return null

        if (keyType is PyLiteralStringType || keyType is PyClassType && ("str" == keyType.name)) {
          val key = element.key
          if (key is PyStringLiteralExpression) {
            strKeysToValueTypes[key.stringValue] = Pair(element.value, valueType)
          }
        }
        else {
          return null
        }
      }

    return PyTypedDictType.createFromKeysToValueTypes(sequence, strKeysToValueTypes)
  }

  private fun getDictLiteralElementTypes(sequence: PyDictLiteralExpression, context: TypeEvalContext): Pair<PyType?, PyType?> {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    val keyTypes = mutableListOf<PyType?>()
    val valueTypes = mutableListOf<PyType?>()

    elements
      .take(maxAnalyzedElements)
      .forEach {
        val type = context.getType(it)
        val (keyType, valueType) = getKeyValueType(type) ?: Pair(null, null)
        keyTypes.add(keyType)
        valueTypes.add(valueType)
      }

    if (elements.size > maxAnalyzedElements) {
      keyTypes.add(null)
      valueTypes.add(null)
    }

    return Pair(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
  }

  private fun getKeyValueType(sequenceElementType: PyType?): Pair<PyType?, PyType?>? {
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
    return null
  }

  private fun createCollectionType(cls: PyClass, vararg elementTypes: PyType?): PyType {
    return PyCollectionTypeImpl(cls, false, elementTypes.map {
      if (it is PyLiteralStringType)
        PyClassTypeImpl(it.cls, false)
      else
        it
    })
  }
}
