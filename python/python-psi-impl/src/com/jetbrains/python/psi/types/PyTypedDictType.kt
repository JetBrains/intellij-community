// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.TDFields
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
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
          if (it.value.qualifiers.isRequired == true) PyCallableParameterImpl.nonPsi(it.key, it.value.type)
          else PyCallableParameterImpl.nonPsi(it.key, it.value.type, ellipsis)
        }
      }
    else null
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

  /**
   * @isRequired is true - if value type is Required, false - if it is NotRequired, and null if it does not have any type specification
   */
  data class TypedDictFieldQualifiers(val isRequired: Boolean? = true, val isReadOnly: Boolean = false)

  data class FieldTypeAndTotality(val value: PyExpression?, val type: PyType?, val qualifiers: TypedDictFieldQualifiers = TypedDictFieldQualifiers())

  companion object {

    const val TYPED_DICT_NAME_PARAMETER = "name"
    const val TYPED_DICT_FIELDS_PARAMETER = "fields"
    const val TYPED_DICT_TOTAL_PARAMETER = "total"

    fun createFromKeysToValueTypes(anchor: PsiElement,
                                   keysToValueTypes: Map<String, Pair<PyExpression?, PyType?>>): PyTypedDictType? {
      return createFromKeysToValueTypes(anchor, keysToValueTypes, true)
    }

    fun createFromKeysToValueTypes(anchor: PsiElement,
                                   keysToValueTypes: Map<String, Pair<PyExpression?, PyType?>>,
                                   inferred: Boolean): PyTypedDictType? {
      val dict = PyBuiltinCache.getInstance(anchor).dictType?.pyClass
      return if (dict != null) {
        val fields = TDFields(keysToValueTypes.entries.associate {
          it.key to FieldTypeAndTotality(it.value.first, it.value.second)
        })
        PyTypedDictType("TypedDict", fields, inferred, dict, DefinitionLevel.INSTANCE, emptyList())
      }
      else null
    }

    /**
     * [actual] matches [expected] if:
     * * all required keys from [expected] are present in [actual]
     * * all keys from [actual] are present in [expected]
     * * each key has the same value type in [expected] and [actual]
     */
    fun checkTypes(expected: PyType,
                   actual: PyTypedDictType,
                   context: TypeEvalContext,
                   value: PyExpression?): TypeCheckingResult? {
      if (!actual.isInferred()) {
        val match = checkStructuralCompatibility(expected, actual, context)
        if (match != null) {
          return match
        }
      }
      if (expected !is PyTypedDictType) return null

      val mandatoryArguments = expected.fields.filterValues { it.qualifiers.isRequired == true}.mapValues { Pair(it.value.value, it.value.type) }
      val actualArguments = actual.getKeysToValuesWithTypes()
      val expectedArguments = expected.getKeysToValuesWithTypes()

      return match(mandatoryArguments, expectedArguments, actualArguments, context, value, expected.name)
    }

    private fun match(mandatoryArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      expectedArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      actualArguments: Map<String, Pair<PyExpression?, PyType?>>,
                      context: TypeEvalContext,
                      actualTypedDict: PyExpression?,
                      expectedTypedDictName: String): TypeCheckingResult {
      val valueTypeErrors = mutableListOf<ValueTypeError>()
      val missingKeys = mutableListOf<MissingKeysError>()
      val extraKeys = mutableListOf<ExtraKeyError>()
      var match = true

      val containedExpression = PyPsiUtils.flattenParens(
        if (actualTypedDict is PyKeywordArgument) actualTypedDict.valueExpression else actualTypedDict)
      val typedDictInstanceCreation = containedExpression is PyDictLiteralExpression ||
                                      containedExpression is PyCallExpression && containedExpression.callee?.textMatches(
        PyNames.DICT) ?: false

      actualArguments.forEach {
        val actualValue = it.value.first
        val key = it.key
        if (!expectedArguments.containsKey(key)) {
          if (typedDictInstanceCreation) {
            val extraKeyToHighlight = PsiTreeUtil.getParentOfType(actualValue, PyKeyValueExpression::class.java)
                                      ?: PsiTreeUtil.getParentOfType(actualValue, PyKeywordArgument::class.java)
                                      ?: actualValue
            extraKeys.add(ExtraKeyError(extraKeyToHighlight, expectedTypedDictName, key))
            match = false
          }
          else {
            return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
          }
        }
        val expectedType = expectedArguments[key]?.second
        val actualType = it.value.second
        if (expectedType is PyTypedDictType && actualType is PyTypedDictType) {
          val res = checkTypes(expectedType, actualType, context, actualValue)
          if (res != null && !res.match) {
            val (innerMatch, innerValueTypeErrors, innerMissingKeys, innerExtraKeys) = res
            if (typedDictInstanceCreation) {
              if (!innerMatch && innerExtraKeys.isEmpty() && innerMissingKeys.isEmpty() && innerValueTypeErrors.isEmpty()) {
                match = false
                valueTypeErrors.add(ValueTypeError(actualValue, expectedType,
                                                   actualType)) // inner TypedDict didn't match, but it's not a dict definition
              }
              match = false
              valueTypeErrors.addAll(innerValueTypeErrors)
              extraKeys.addAll(innerExtraKeys)
              missingKeys.addAll(innerMissingKeys)
            }
            else if (!innerMatch) {
              return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
            }
          }
        }
        val matchResult: Boolean = strictUnionMatch(
          expectedType,
          if (actualValue != null) PyLiteralType.promoteToLiteral(actualValue, expectedType, context, null) ?: context.getType(actualValue)
          else it.value.second,
          context
        )
        if (!matchResult && (expectedType !is PyTypedDictType || actualType !is PyTypedDictType)) {
          if (typedDictInstanceCreation) {
            valueTypeErrors.add(
              ValueTypeError(actualValue, expectedType, it.value.second))
            match = false
          }
          else {
            return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
          }
        }
      }

      if (!actualArguments.keys.containsAll(mandatoryArguments.keys)) {
        if (typedDictInstanceCreation) {
          missingKeys.add(MissingKeysError(actualTypedDict, expectedTypedDictName, mandatoryArguments.keys.filter {
            !actualArguments.containsKey(it)
          }))
          match = false
        }
        else {
          return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
        }
      }

      return if (typedDictInstanceCreation) TypeCheckingResult(match, valueTypeErrors, missingKeys, extraKeys)
      else TypeCheckingResult(true, emptyList(), emptyList(), emptyList())
    }

    private fun strictUnionMatch(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
      return PyTypeUtil.toStream(actual).allMatch { type -> PyTypeChecker.match(expected, type, context) }
    }

    /**
     * Rules for type-checking TypedDicts are described in PEP-589
     * @see <a href=https://www.python.org/dev/peps/pep-0589/#type-consistency>PEP-589</a>
     */
    private fun checkStructuralCompatibility(expected: PyType?,
                                             actual: PyTypedDictType,
                                             context: TypeEvalContext): TypeCheckingResult? {
      if (expected is PyCollectionType && PyTypingTypeProvider.MAPPING == expected.classQName) {
        val builtinCache = PyBuiltinCache.getInstance(actual.dictClass)
        val elementTypes = expected.elementTypes
        return TypeCheckingResult(elementTypes.size == 2
                                  && builtinCache.strType == elementTypes[0]
                                  && (elementTypes[1] == null || PyNames.OBJECT == elementTypes[1].name), emptyList(),
                                  emptyList(), emptyList())
      }

      if (expected !is PyTypedDictType) return null

      expected.fields.forEach {
        val expectedTypeAndTotality = it.value
        val actualTypeAndTotality = actual.fields[it.key]

        if (actualTypeAndTotality == null
            || !strictUnionMatch(expectedTypeAndTotality.type, actualTypeAndTotality.type, context)
            || !strictUnionMatch(actualTypeAndTotality.type, expectedTypeAndTotality.type, context)
            || expectedTypeAndTotality.qualifiers.isRequired != actualTypeAndTotality.qualifiers.isRequired) {
          return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
        }

        if (!actual.fields.containsKey(it.key)) {
          return TypeCheckingResult(false, emptyList(), emptyList(), emptyList())
        }
      }
      return TypeCheckingResult(true, emptyList(), emptyList(), emptyList())
    }
  }

  data class MissingKeysError(val actualExpression: PyExpression?, val expectedTypedDictName: String, val missingKeys: List<String>)

  data class ExtraKeyError(val actualExpression: PyExpression?, val expectedTypedDictName: String, val key: String)

  data class ValueTypeError(val actualExpression: PyExpression?, val expectedType: PyType?, val actualType: PyType?)

  data class TypeCheckingResult(val match: Boolean,
                                val valueTypeErrors: List<ValueTypeError>,
                                val missingKeys: List<MissingKeysError>,
                                val extraKeys: List<ExtraKeyError>)
}
