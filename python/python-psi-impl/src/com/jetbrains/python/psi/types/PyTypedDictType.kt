// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBoolLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPassStatementImpl
import one.util.streamex.StreamEx
import java.util.*

class PyTypedDictType @JvmOverloads constructor(private val name: String,
                                                val fields: LinkedHashMap<String, FieldTypeAndTotality>,
                                                private val inferred: Boolean,
                                                private val dictClass: PyClass,
                                                private val definitionLevel: DefinitionLevel,
                                                private val ancestors: List<PyTypedDictType>,
                                                private val targetExpression: PyTargetExpression? = null) : PyClassTypeImpl(dictClass,
                                                                                                                            definitionLevel != DefinitionLevel.INSTANCE), PyCollectionType {
  override fun getElementTypes(): List<PyType?> {
    return listOf(PyBuiltinCache.getInstance(dictClass).strType, getValuesType())
  }

  override fun getIteratedItemType(): PyType? {
    return PyBuiltinCache.getInstance(dictClass).strType
  }

  fun getValuesType(): PyType? {
    return PyUnionType.union(fields.map { it.value.type })
  }

  fun getElementType(key: String): PyType? {
    return fields[key]?.type
  }

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteExpression): PyType? {
    if (definitionLevel == DefinitionLevel.NEW_TYPE) {
      return toInstance()
    }

    return null
  }

  override fun isDefinition(): Boolean {
    return definitionLevel == DefinitionLevel.NEW_TYPE
  }

  override fun toInstance(): PyClassType {
    return if (definitionLevel == DefinitionLevel.NEW_TYPE)
      PyTypedDictType(name, fields, inferred, dictClass,
                      DefinitionLevel.INSTANCE, ancestors,
                      targetExpression)
    else
      this
  }

  override fun toClass(): PyClassLikeType {
    return if (definitionLevel == DefinitionLevel.INSTANCE)
      PyTypedDictType(name, fields, inferred, dictClass,
                      DefinitionLevel.NEW_TYPE, ancestors,
                      targetExpression)
    else
      this
  }

  override fun getName(): String? {
    return name
  }

  override fun isBuiltin(): Boolean {
    return false
  }

  override fun isCallable(): Boolean {
    return definitionLevel != DefinitionLevel.INSTANCE
  }

  override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
    val elementGenerator = PyElementGenerator.getInstance(dictClass.project)
    val psi = PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter())
    val ellipsis = elementGenerator.createEllipsis()
    return if (isCallable)
      listOf(psi) + fields.map {
        if (it.value.isRequired) PyCallableParameterImpl.nonPsi(it.key, it.value.type)
        else {
          PyCallableParameterImpl.nonPsi(it.key, it.value.type, ellipsis)
        }
      }
    else
      null
  }

  private fun getKeysToValueTypes(): Map<String, PyType?> {
    return fields.mapValues { it.value.type }
  }

  override fun toString(): String {
    return "PyTypedDictType: $name"
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null || javaClass != other.javaClass) return false

    val otherTypedDict = other as? PyTypedDictType ?: return false
    return name == otherTypedDict.name
           && fields == otherTypedDict.fields
           && inferred == otherTypedDict.inferred
           && definitionLevel == otherTypedDict.definitionLevel
           && ancestors == otherTypedDict.ancestors
           && targetExpression == otherTypedDict.targetExpression
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), name, fields, inferred, definitionLevel, ancestors, targetExpression)
  }

  enum class DefinitionLevel {
    NEW_TYPE,
    INSTANCE
  }

  /**
   * Is this an actual TypedDict type or something that is inferred to match the expected TypedDict type (e.g. [PyDictLiteralExpression])
   */
  fun isInferred(): Boolean {
    return inferred
  }

  class FieldTypeAndTotality @JvmOverloads constructor(val type: PyType?, val isRequired: Boolean = true)

  companion object {

    /**
     * [actual] matches [expected] if:
     * * all required keys from [expected] are present in [actual]
     * * all keys from [actual] are present in [expected]
     * * each key has the same value type in [expected] and [actual]
     */
    fun match(expected: PyTypedDictType, actual: PyTypedDictType, context: TypeEvalContext): Boolean {
      val mandatoryArguments = expected.fields.filterValues { it.isRequired }.mapValues { it.value.type }
      val actualArguments = actual.getKeysToValueTypes()
      val expectedArguments = expected.getKeysToValueTypes()

      return match(mandatoryArguments, expectedArguments, actualArguments, context)
    }

    fun match(expected: PyTypedDictType, actual: PyDictLiteralExpression, context: TypeEvalContext): Boolean {
      if (actual.elements.any { it.key !is PyStringLiteralExpression }) return false
      val mandatoryArguments = expected.fields.filter { it.value.isRequired }.map { it.key to it.value.type }.toMap()
      val actualArguments = actual.elements.map {
        (it.key as PyStringLiteralExpression).stringValue to if (it.value != null) context.getType(it.value!!) else null
      }.toMap()
      val expectedArguments = expected.getKeysToValueTypes()

      return match(mandatoryArguments, expectedArguments, actualArguments, context)
    }

    private fun match(mandatoryArguments: Map<String, PyType?>,
                      expectedArguments: Map<String, PyType?>,
                      actualArguments: Map<String, PyType?>,
                      context: TypeEvalContext): Boolean {
      if (!actualArguments.keys.containsAll(mandatoryArguments.keys)) return false

      actualArguments.forEach {
        if (!expectedArguments.containsKey(it.key)) {
          return false
        }
        val matchResult: Boolean = strictUnionMatch(expectedArguments[it.key], it.value, context)
        if (!matchResult) {
          return false
        }
      }

      return true
    }

    private fun strictUnionMatch(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
      if (actual is PyUnionType) {
        return StreamEx.of(actual.members).allMatch { type -> PyTypeChecker.match(expected, type, context) }
      }

      return PyTypeChecker.match(expected, actual, context)
    }

    fun checkStructuralCompatibility(expected: PyType?, actual: PyTypedDictType, context: TypeEvalContext): Optional<Boolean> {
      if (expected is PyCollectionType && PyTypingTypeProvider.MAPPING == expected.classQName) {
        val builtinCache = PyBuiltinCache.getInstance(actual.dictClass)
        val elementTypes = expected.elementTypes
        return Optional.of(elementTypes.size == 2
                           && builtinCache.strType == elementTypes[0]
                           && (elementTypes[1] == null || PyNames.OBJECT == elementTypes[1].name))
      }

      if (expected !is PyTypedDictType) return Optional.empty()

      expected.fields.forEach {
        val expectedTypeAndTotality = it.value

        if (!actual.fields.containsKey(it.key)) return Optional.of(false)

        val actualTypeAndTotality = actual.fields[it.key]
        if (actualTypeAndTotality == null
            || !strictUnionMatch(expectedTypeAndTotality.type, actualTypeAndTotality.type, context)
            || !strictUnionMatch(actualTypeAndTotality.type, expectedTypeAndTotality.type, context)
            || expectedTypeAndTotality.isRequired.xor(actualTypeAndTotality.isRequired)) {
          return Optional.of(false)
        }
      }
      return Optional.of(true)
    }
  }
}
