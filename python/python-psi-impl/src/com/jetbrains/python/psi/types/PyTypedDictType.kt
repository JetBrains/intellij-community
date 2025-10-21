// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.jetbrains.annotations.ApiStatus
import java.util.*

class PyTypedDictType @JvmOverloads constructor(
  private val name: String,
  val fields: Map<String, FieldTypeAndTotality>,
  
  private val dictClass: PyClass,
  private val definitionLevel: DefinitionLevel,
  private val ancestors: List<PyTypedDictType>,
  private val declaration: PyQualifiedNameOwner? = null,
) :
  PyClassTypeImpl(dictClass, definitionLevel != DefinitionLevel.INSTANCE){
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
      PyTypedDictType(name, fields, dictClass,
                      DefinitionLevel.INSTANCE, ancestors,
                      declaration)
    else
      this
  }

  override fun toClass(): PyClassLikeType {
    return if (definitionLevel == DefinitionLevel.INSTANCE)
      PyTypedDictType(name, fields, dictClass,
                      DefinitionLevel.NEW_TYPE, ancestors,
                      declaration)
    else
      this
  }

  override fun getName(): String {
    return name
  }

  override fun isBuiltin(): Boolean = false

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

  override fun toString(): String {
    return "PyTypedDictType: $name"
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null || javaClass != other.javaClass) return false

    val otherTypedDict = other as? PyTypedDictType ?: return false
    return name == otherTypedDict.name
           && fields == otherTypedDict.fields
           && definitionLevel == otherTypedDict.definitionLevel
           && ancestors == otherTypedDict.ancestors
           && declaration == otherTypedDict.declaration
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), name, fields, definitionLevel, ancestors, declaration)
  }

  enum class DefinitionLevel {
    NEW_TYPE,
    INSTANCE
  }

  override fun getDeclarationElement(): PyQualifiedNameOwner = declaration ?: super<PyClassTypeImpl>.getDeclarationElement()

  /**
   * @isRequired is true - if value type is Required, false - if it is NotRequired, and null if it does not have any type specification
   */
  data class TypedDictFieldQualifiers(val isRequired: Boolean? = true, val isReadOnly: Boolean = false)

  data class FieldTypeAndTotality(val value: PyExpression?, val type: PyType?, val qualifiers: TypedDictFieldQualifiers = TypedDictFieldQualifiers()) {
    val isRequired: Boolean get() = qualifiers.isRequired ?: true
    val isReadOnly: Boolean get() = qualifiers.isReadOnly
  }

  companion object {

    const val TYPED_DICT_TOTAL_PARAMETER: String = "total"

    /**
     * [expression] matches [expectedType] if:
     * * all required keys from [expectedType] are present in [expression]
     * * all keys from [expression] are present in [expectedType]
     * * each key has the same value type in [expectedType] and [expression]
     */
    @ApiStatus.Internal
    @JvmStatic
    fun checkExpression(
      expectedType: PyTypedDictType,
      expression: PyExpression,
      context: TypeEvalContext,
      result: TypeCheckingResult,
    ) {
      assert(isDictExpression(expression, context))
      val actualFields = getTypedDictFieldsFromExpression(expression, context)
      if (actualFields == null) {
        result.valueTypeErrors.add(ValueTypeError(expression, expectedType, context.getType(expression)))
        return
      }

      actualFields.forEach {
        val key = it.key
        val (actualFieldValue, actualFieldType) = it.value
        if (expectedType.fields.containsKey(key)) {
          val expectedFieldType = expectedType.fields[key]?.type
          if (expectedFieldType is PyTypedDictType && actualFieldValue != null && isDictExpression(actualFieldValue, context)) {
            checkExpression(expectedFieldType, actualFieldValue, context, result)
          }
          else {
            val promotedType = if (actualFieldValue != null) {
              PyLiteralType.promoteToLiteral(actualFieldValue, expectedFieldType, context, null) ?: context.getType(actualFieldValue)
            }
            else {
              actualFieldType
            }
            if (!match(expectedFieldType, promotedType, actualFieldValue, context, result)) {
              result.valueTypeErrors.add(ValueTypeError(actualFieldValue, expectedFieldType, actualFieldType))
            }
          }
        }
        else {
          val extraKeyToHighlight = PsiTreeUtil.getParentOfType(actualFieldValue, PyKeyValueExpression::class.java)
                                    ?: PsiTreeUtil.getParentOfType(actualFieldValue, PyKeywordArgument::class.java)
                                    ?: actualFieldValue
          result.extraKeys.add(ExtraKeyError(extraKeyToHighlight, expectedType.name, key))
        }
      }

      val missingKeys = expectedType.fields.entries
        .filter { (key, value) -> value.qualifiers.isRequired == true && key !in actualFields }
        .map { it.key }
      if (missingKeys.isNotEmpty()) {
        result.missingKeys.add(MissingKeysError(expression, expectedType.name, missingKeys))
      }
    }

    private fun match(
      expectedType: PyType?,
      actualType: PyType?,
      actualExpression: PyExpression?,
      context: TypeEvalContext,
      result: TypeCheckingResult,
    ): Boolean {
      if (actualExpression != null && isDictExpression(actualExpression, context)) {
        val types = PyTypeUtil.toStream(expectedType).toList()
        for (subType in types) {
          if (subType is PyTypedDictType) {
            val res = if (types.size <= 1) result else TypeCheckingResult()
            checkExpression(subType, actualExpression, context, res)
            if (!res.hasErrors) {
              return true
            }
          }
          else {
            if (strictUnionMatch(subType, actualType, context)) {
              return true
            }
          }
        }
        return false
      }
      return strictUnionMatch(expectedType, actualType, context)
    }

    private fun strictUnionMatch(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
      if (!PyUnionType.isStrictSemanticsEnabled()) {
        return PyTypeUtil.toStream(actual).allMatch { type -> PyTypeChecker.match(expected, type, context) }
      }
      return PyTypeChecker.match(expected, actual, context)
    }

    /**
     * Rules for type-checking TypedDicts are described in PEP-589
     * @see <a href=https://www.python.org/dev/peps/pep-0589/#type-consistency>PEP-589</a>
     */
    @ApiStatus.Internal
    @JvmStatic
    fun match(
      expected: PyType,
      actual: PyTypedDictType,
      context: TypeEvalContext,
    ): Boolean? {
      if (expected is PyCollectionType && PyTypingTypeProvider.MAPPING == expected.classQName) {
        val builtinCache = PyBuiltinCache.getInstance(actual.dictClass)
        val elementTypes = expected.elementTypes
        return elementTypes.size == 2
               && builtinCache.strType == elementTypes[0]
               && (elementTypes[1] == null || PyNames.OBJECT == elementTypes[1].name)
      }

      if (expected is PyClassLikeType && isProtocol(expected, context)) {
        return null
      }

      if (expected !is PyTypedDictType) {
        return false
      }

      for ((expectedKey, expectedField) in expected.fields) {
        if (expectedField.isReadOnly && !expectedField.isRequired && expectedField.type?.name == PyNames.OBJECT) {
          continue
        }
        val actualField = actual.fields[expectedKey]
        if (actualField == null) {
          return false
        }
        if (!strictUnionMatch(expectedField.type, actualField.type, context)) {
          return false
        }
        if (!expectedField.isReadOnly) {
          if (!(strictUnionMatch(actualField.type, expectedField.type, context) && !actualField.isReadOnly)) {
            return false
          }
        }
        if (expectedField.isRequired) {
          if (!actualField.isRequired) {
            return false
          }
        }
        else {
          if (!expectedField.isReadOnly) {
            if (actualField.isRequired) {
              return false
            }
          }
        }
      }
      return true
    }

    @ApiStatus.Internal
    @JvmStatic
    fun isDictExpression(expression: PyExpression, context: TypeEvalContext): Boolean {
      if (expression is PyDictLiteralExpression) return true
      if (expression is PyCallExpression) {
        val callee = expression.callee
        if (callee != null) {
          return PyTypingTypeProvider.resolveToQualifiedNames(callee, context).any { it == PyNames.DICT }
        }
      }
      return false
    }

    private fun getTypedDictFieldsFromExpression(
      expression: PyExpression,
      context: TypeEvalContext,
    ): Map<String, Pair<PyExpression?, PyType?>>? {
      assert(isDictExpression(expression, context))
      return if (expression is PyDictLiteralExpression) {
        PyCollectionTypeUtil.getTypedDictFieldsFromDictLiteral(expression, context)
      }
      else {
        getTypedDictFieldsFromDictConstructorCall(expression as PyCallExpression, context)
      }
    }

    private fun getTypedDictFieldsFromDictConstructorCall(
      callExpression: PyCallExpression,
      context: TypeEvalContext,
    ): Map<String, Pair<PyExpression?, PyType?>>? {
      val callee = callExpression.callee ?: return null
      if (PyTypingTypeProvider.resolveToQualifiedNames(callee, context).any { it == PyNames.DICT }) {
        val arguments = callExpression.arguments
        if (arguments.size > 1) {
          val fields = LinkedHashMap<String, Pair<PyExpression?, PyType?>>()
          for (argument in arguments) {
            if (argument !is PyKeywordArgument) return null
            val keyword = argument.keyword ?: return null
            val valueExpression = argument.valueExpression
            fields[keyword] = valueExpression to valueExpression?.let(context::getType)
          }
          return fields
        }
      }
      return null
    }
  }

  data class MissingKeysError(val actualExpression: PyExpression?, val expectedTypedDictName: String, val missingKeys: List<String>)

  data class ExtraKeyError(val actualExpression: PyExpression?, val expectedTypedDictName: String, val key: String)

  data class ValueTypeError(val actualExpression: PyExpression?, val expectedType: PyType?, val actualType: PyType?)

  class TypeCheckingResult {
    val valueTypeErrors: MutableList<ValueTypeError> = mutableListOf()
    val missingKeys: MutableList<MissingKeysError> = mutableListOf()
    val extraKeys: MutableList<ExtraKeyError> = mutableListOf()

    val hasErrors: Boolean get() = valueTypeErrors.isNotEmpty() || missingKeys.isNotEmpty() || extraKeys.isNotEmpty()
  }

  override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyTypedDictType(this)
    }
    return visitor.visitPyClassType(this)
  }

}
