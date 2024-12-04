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
    var allStrKeys = true
    val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()

    elements
      .take(maxAnalyzedElements)
      .map { element -> Pair(element, context.getType(element) as? PyTupleType) }
      .forEach { (tuple, tupleType) ->
        if (tupleType != null) {
          val tupleElementTypes = tupleType.elementTypes
          when {
            tupleType.isHomogeneous -> {
              val keyAndValueType = tupleType.iteratedItemType
              val keysToValueTypes = assemblePotentialTypedDictFields(keyAndValueType, keyAndValueType, tuple)
              if (keysToValueTypes != null) {
                strKeysToValueTypes.putAll(keysToValueTypes)
              }
              else {
                allStrKeys = false
              }
            }
            tupleElementTypes.size == 2 -> {
              val keysToValueTypes = assemblePotentialTypedDictFields(tupleElementTypes[0], tupleElementTypes[1], tuple)
              if (keysToValueTypes != null) {
                strKeysToValueTypes.putAll(keysToValueTypes)
              }
              else {
                allStrKeys = false
              }
            }
            else -> {
              allStrKeys = false
            }
          }
        }
        else {
          allStrKeys = false
        }
      }

    if (elements.size > maxAnalyzedElements) {
      allStrKeys = false
    }

    return if (allStrKeys) PyTypedDictType.createFromKeysToValueTypes(sequence, strKeysToValueTypes) else null
  }

  private fun getDictLiteralElementTypes(sequence: PyDictLiteralExpression, context: TypeEvalContext): Pair<PyType?, PyType?> {
    val elements = sequence.elements
    val maxAnalyzedElements = MAX_ANALYZED_ELEMENTS_OF_LITERALS.coerceAtMost(elements.size)
    val keyTypes = ArrayList<PyType?>()
    val valueTypes = ArrayList<PyType?>()

    elements
      .take(maxAnalyzedElements)
      .map { element -> context.getType(element) as? PyTupleType }
      .forEach { tupleType ->
        if (tupleType != null) {
          val tupleElementTypes = tupleType.elementTypes
          when {
            tupleType.isHomogeneous -> {
              val keyAndValueType = tupleType.iteratedItemType
              keyTypes.add(keyAndValueType)
              valueTypes.add(keyAndValueType)
            }
            tupleElementTypes.size == 2 -> {
              val keyType = tupleElementTypes[0]
              val valueType = tupleElementTypes[1]
              keyTypes.add(keyType)
              valueTypes.add(valueType)
            }
            else -> {
              keyTypes.add(null)
              valueTypes.add(null)
            }
          }
        }
        else {
          keyTypes.add(null)
          valueTypes.add(null)
        }
      }

    if (elements.size > maxAnalyzedElements) {
      keyTypes.add(null)
      valueTypes.add(null)
    }

    return Pair(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))
  }

  private fun assemblePotentialTypedDictFields(keyType: PyType?,
                                               valueType: PyType?,
                                               tuple: PyExpression): Map<String, Pair<PyExpression?, PyType?>>? {
    val strKeysToValueTypes = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()
    var allStrKeys = true

    if (keyType is PyLiteralStringType || keyType is PyClassType && ("str" == keyType.name)) {
      when (tuple) {
        is PyKeyValueExpression -> {
          if (tuple.key is PyStringLiteralExpression) {
            strKeysToValueTypes[(tuple.key as PyStringLiteralExpression).stringValue] = Pair(tuple.value, valueType)
          }
        }
        is PyTupleExpression -> {
          val tupleElements = tuple.elements
          if (tupleElements.size > 1 && tupleElements[0] is PyStringLiteralExpression) {
            strKeysToValueTypes[(tupleElements[0] as PyStringLiteralExpression).stringValue] = Pair(tupleElements[1], valueType)
          }
        }
      }
    }
    else {
      allStrKeys = false
    }

    return if (allStrKeys) strKeysToValueTypes else null
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
