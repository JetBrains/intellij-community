// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.TDFields
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import java.util.*

class PyTypedDictType @JvmOverloads constructor(private val name: String,
                                                val fields: LinkedHashMap<String, FieldTypeAndTotality>,
                                                private val inferred: Boolean,
                                                private val dictClass: PyClass,
                                                private val definitionLevel: DefinitionLevel,
                                                private val ancestors: List<PyTypedDictType>,
                                                private val declaration: PyQualifiedNameOwner? = null) : PyClassTypeImpl(dictClass,
                                                                                                                         definitionLevel != DefinitionLevel.INSTANCE), PyCollectionType {
  override fun getElementTypes(): List<PyType?> {
    return listOf(if (!inferred || fields.isNotEmpty()) PyBuiltinCache.getInstance(dictClass).strType else null, getValuesType())
  }

  override fun getIteratedItemType(): PyType? {
    return PyBuiltinCache.getInstance(dictClass).strType
  }

  private fun getValuesType(): PyType? {
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
                      declaration)
    else
      this
  }

  override fun toClass(): PyClassLikeType {
    return if (definitionLevel == DefinitionLevel.INSTANCE)
      PyTypedDictType(name, fields, inferred, dictClass,
                      DefinitionLevel.NEW_TYPE, ancestors,
                      declaration)
    else
      this
  }

  override fun getName(): String {
    return name
  }

  override fun isBuiltin(): Boolean {
    return inferred // if TD is inferred then it's a dict with str-only keys
  }

  override fun isCallable(): Boolean {
    return definitionLevel != DefinitionLevel.INSTANCE
  }

  override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
    return if (isCallable)
      if (fields.isEmpty()) emptyList()
      else {
        val elementGenerator = PyElementGenerator.getInstance(dictClass.project)
        val singleStarParameter = PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter())
        val ellipsis = elementGenerator.createEllipsis()
        listOf(singleStarParameter) + fields.map {
          if (it.value.isRequired) PyCallableParameterImpl.nonPsi(it.key, it.value.type)
          else PyCallableParameterImpl.nonPsi(it.key, it.value.type, ellipsis)
        }
      }
    else null
  }

  fun getKeysToValueTypes(): Map<String, PyType?> {
    return fields.mapValues { it.value.type }
  }

  fun getKeysToValuesWithTypes(): Map<String, Pair<PyExpression?, PyType?>> {
    return fields.mapValues { Pair(it.value.value, it.value.type) }
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
           && declaration == otherTypedDict.declaration
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), name, fields, inferred, definitionLevel, ancestors, declaration)
  }

  enum class DefinitionLevel {
    NEW_TYPE,
    INSTANCE
  }

  /**
   * Checks whether this is an actual TypedDict type or [PyDictLiteralExpression] with all str keys
   */
  fun isInferred(): Boolean {
    return inferred
  }

  override fun getDeclarationElement(): PyQualifiedNameOwner = declaration ?: super<PyClassTypeImpl>.getDeclarationElement()

  class FieldTypeAndTotality @JvmOverloads constructor(val value: PyExpression?, val type: PyType?, val isRequired: Boolean = true)

  companion object {

    const val TYPED_DICT_NAME_PARAMETER = "name"
    const val TYPED_DICT_FIELDS_PARAMETER = "fields"
    const val TYPED_DICT_TOTAL_PARAMETER = "total"

    fun createFromKeysToValueTypes(anchor: PsiElement,
                                   keysToValueTypes: Map<String, Pair<PyExpression?, PyType?>>): PyTypedDictType? {
      val dict = PyBuiltinCache.getInstance(anchor).dictType?.pyClass
      return if (dict != null) {
        val fields = TDFields(keysToValueTypes.entries.associate {
          it.key to FieldTypeAndTotality(it.value.first, it.value.second)
        })
        PyTypedDictType("TypedDict", fields, true, dict, DefinitionLevel.INSTANCE, emptyList())
      }
      else null
    }

    /**
     * [actual] matches [expected] if:
     * * all required keys from [expected] are present in [actual]
     * * all keys from [actual] are present in [expected]
     * * each key has the same value type in [expected] and [actual]
     */
    fun checkTypes(expected: PyType, actual: PyTypedDictType, context: TypeEvalContext): Optional<TypeCheckingResult> {
      if (!actual.isInferred()) {
        val match = checkStructuralCompatibility(expected, actual, context)
        if (match.isPresent) {
          return match
        }
      }
      if (expected !is PyTypedDictType) return Optional.empty()

      val mandatoryArguments = expected.fields.filterValues { it.isRequired }.mapValues { Pair(it.value.value, it.value.type) }
      val actualArguments = actual.getKeysToValuesWithTypes()
      val expectedArguments = expected.getKeysToValuesWithTypes()

      return Optional.of(match(mandatoryArguments, expectedArguments, actualArguments, context))
    }

    private fun match(mandatoryArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      expectedArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      actualArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      context: TypeEvalContext): TypeCheckingResult {
      val valueTypesErrors = mutableListOf<ValueTypeError>()
      val keysMissing = mutableListOf<String>()
      val extraKeys = mutableListOf<String>()
      var match = true

      if (!actualArguments.keys.containsAll(mandatoryArguments.keys)) {
        keysMissing.addAll(mandatoryArguments.keys.filter { !actualArguments.containsKey(it) })
        match = false
      }

      actualArguments.forEach {
        if (!expectedArguments.containsKey(it.key)) {
          extraKeys.add(it.key)
          match = false
        }
        val actualValue = it.value.first
        val expectedType = expectedArguments[it.key]?.second
        val matchResult: Boolean = strictUnionMatch(
          expectedType,
          if (actualValue != null) PyLiteralType.promoteToLiteral(actualValue, expectedType, context, null) ?: context.getType(actualValue)
          else it.value.second,
          context
        )
        if (!matchResult) {
          valueTypesErrors.add(ValueTypeError(it.value.first, expectedType, it.value.second))
          match = false
        }
      }

      return TypeCheckingResult(match, valueTypesErrors, keysMissing, extraKeys)
    }

    private fun strictUnionMatch(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
      return PyTypeUtil.toStream(actual).allMatch { type -> PyTypeChecker.match(expected, type, context) }
    }

    /**
     * Rules for type-checking TypedDicts are described in PEP-589
     * @see <a href=https://www.python.org/dev/peps/pep-0589/#type-consistency>PEP-589</a>
     */
    fun checkStructuralCompatibility(expected: PyType?,
                                     actual: PyTypedDictType,
                                     context: TypeEvalContext): Optional<TypeCheckingResult> {
      if (expected is PyCollectionType && PyTypingTypeProvider.MAPPING == expected.classQName) {
        val builtinCache = PyBuiltinCache.getInstance(actual.dictClass)
        val elementTypes = expected.elementTypes
        return Optional.of(TypeCheckingResult(elementTypes.size == 2
                                              && builtinCache.strType == elementTypes[0]
                                              && (elementTypes[1] == null || PyNames.OBJECT == elementTypes[1].name), emptyList(), emptyList(), emptyList()))
      }

      if (expected !is PyTypedDictType) return Optional.empty()

      val valueTypesErrors = mutableListOf<ValueTypeError>()
      val keysMissing = mutableListOf<String>()
      var match = true

      expected.fields.forEach {
        if (!actual.fields.containsKey(it.key)) {
          keysMissing.add(it.key)
          match = false
        }

        val expectedTypeAndTotality = it.value

        val actualTypeAndTotality = actual.fields[it.key]
        if (actualTypeAndTotality == null
            || !strictUnionMatch(expectedTypeAndTotality.type, actualTypeAndTotality.type, context)
            || !strictUnionMatch(actualTypeAndTotality.type, expectedTypeAndTotality.type, context)
            || expectedTypeAndTotality.isRequired.xor(actualTypeAndTotality.isRequired)) {
          valueTypesErrors.add(ValueTypeError(null, expectedTypeAndTotality.type, actualTypeAndTotality?.type))
          match = false
        }
      }
      return Optional.of(TypeCheckingResult(match, valueTypesErrors, keysMissing, emptyList()))
    }
  }

  class ValueTypeError constructor(val actualExpression: PyExpression?,
                                   val expectedType: PyType?,
                                   val actualType: PyType?)

  class TypeCheckingResult constructor(val match: Boolean,
                                       val valueTypesErrors: List<ValueTypeError>,
                                       val missingKeys: List<String>,
                                       val extraKeys: List<String>)
}
