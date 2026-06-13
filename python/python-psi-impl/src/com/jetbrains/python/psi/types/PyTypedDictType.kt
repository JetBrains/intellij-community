// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import org.jetbrains.annotations.ApiStatus
import java.util.Objects

class PyTypedDictType(
  override val name: String,
  val fields: Map<String, FieldTypeAndTotality>,
  private val dictClass: PyClass,
  isDefinition: Boolean,
  private val declaration: PyQualifiedNameOwner,
  val isClosed: Boolean = false,
  val extraItemsType: PyType? = null,
  val extraItemsQualifiers: TypedDictFieldQualifiers = TypedDictFieldQualifiers(),
) : PyClassTypeImpl(dictClass, isDefinition) {
  fun getElementType(key: String): PyType? {
    return fields[key]?.type
  }

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteOwner): PyType? {
    return if (isDefinition) toInstance() else null
  }

  override fun toInstance(): PyTypedDictType {
    return if (isDefinition)
      PyTypedDictType(name, fields, dictClass, false, declaration, isClosed, extraItemsType, extraItemsQualifiers)
    else
      this
  }

  override fun toClass(): PyTypedDictType {
    return if (isDefinition)
      this
    else
      PyTypedDictType(name, fields, dictClass, true, declaration, isClosed, extraItemsType, extraItemsQualifiers)
  }

  override val isBuiltin: Boolean = false

  override fun isCallable(): Boolean = isDefinition

  override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
    return if (isCallable) {
      if (fields.isEmpty() && extraItemsType == null) {
        emptyList()
      }
      else {
        val singleStarParameter = PyCallableParameterImpl.keywordOnlySeparatorNonPsi()

        val fieldParameters = fields.map { (key, value) ->
          if (value.qualifiers.isRequired == true)
            PyCallableParameterImpl.nonPsi(key, value.type)
          else
            PyCallableParameterImpl.nonPsi(key, value.type, PyNames.ELLIPSIS)
        }

        val extraItemsParam = if (extraItemsType != null && !isClosed) {
          listOf(PyCallableParameterImpl.keywordContainerNonPsi("kwargs", extraItemsType))
        } else {
          emptyList()
        }

        listOf(singleStarParameter) + fieldParameters + extraItemsParam
      }
    }
    else null
  }

  override fun getParametersType(context: TypeEvalContext): PyCallableParameterVariadicType? {
    return getParameters(context)?.let { PyCallableParameterListTypeImpl(it) }
  }

  override fun toString(): String = "PyTypedDictType: $name"

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null || javaClass != other.javaClass) return false
    if (!super.equals(other)) return false

    other as PyTypedDictType
    return declaration == other.declaration
  }

  override fun hashCode(): Int {
    return Objects.hash(super.hashCode(), declaration)
  }

  override val declarationElement: PyQualifiedNameOwner = declaration

  /**
   * @isRequired is true - if value type is Required, false - if it is NotRequired, and null if it does not have any type specification
   */
  data class TypedDictFieldQualifiers(
    val isRequired: Boolean? = true,
    val isReadOnly: Boolean = false,
    val hasExplicitRequiredQualifier: Boolean = false
  )

  data class FieldTypeAndTotality(
    val value: PyExpression?,
    val type: PyType?,
    val qualifiers: TypedDictFieldQualifiers = TypedDictFieldQualifiers(),
  ) {
    val isRequired: Boolean get() = qualifiers.isRequired ?: true
    val isReadOnly: Boolean get() = qualifiers.isReadOnly
  }

  companion object {

    const val TYPED_DICT_TOTAL_PARAMETER: String = "total"
    const val TYPED_DICT_EXTRA_ITEMS_PARAMETER: String = "extra_items"
    const val TYPED_DICT_CLOSED_PARAMETER : String  = "closed"

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

      val extraItemsType = expectedType.extraItemsType
      val isClosed = expectedType.isClosed

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
        else if (extraItemsType != null && !isClosed) {
          if (!match(extraItemsType, actualFieldType, actualFieldValue, context, result)) {
            result.valueTypeErrors.add(ValueTypeError(actualFieldValue, extraItemsType, actualFieldType))
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
        val types = expectedType.toStream().toList()
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
        return actual.toStream().allMatch { type -> PyTypeChecker.match(expected, type, context) }
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
      if (expected is PyCollectionType) {
        matchTypedDictWithCollection(expected, actual, context)?.let { return it }
      }

      if (expected is PyClassLikeType && expected.isProtocol(context)) {
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

    private fun matchTypedDictWithCollection(expected: PyCollectionType, actual: PyTypedDictType, context: TypeEvalContext): Boolean? {
      val expectedClassQName = expected.classQName
      if (expectedClassQName != PyTypingTypeProvider.MAPPING && expectedClassQName != PyNames.DICT) return null

      val builtinCache = PyBuiltinCache.getInstance(actual.dictClass)
      val elementTypes = expected.elementTypes

      if (elementTypes.size != 2 || builtinCache.strType != elementTypes[0]) {
        return false
      }

      val expectedValueType = elementTypes[1]
      val extraItemsType = actual.extraItemsType
      val hasExtraItems = extraItemsType != null && extraItemsType != PyNeverType.NEVER

      val allValueTypes = mutableListOf<PyType?>()
      allValueTypes.addAll(actual.fields.values.mapNotNull { it.type })

      if (extraItemsType != null && !actual.isClosed) {
        allValueTypes.add(extraItemsType)
      }

      val unionOfFieldTypes = PyUnionType.union(allValueTypes)

      if (PyTypingTypeProvider.MAPPING == expectedClassQName) {
        if (hasExtraItems && !actual.isClosed) {
          return PyTypeChecker.match(expectedValueType, unionOfFieldTypes, context)
        }
        else {
          return elementTypes[1] == null || PyNames.OBJECT == elementTypes[1].name
        }
      }
      else {
        // A TypedDict is generally not assignable to `dict[str, X]` because `dict` is mutable and
        // invariant, so its known keys could be deleted or have their value types broken. However,
        // `dict[str, Any]` uses `Any` as the value type, which opts out of value-type checking
        // (the common "JSON-like" usage), so accept any TypedDict here. See PY-85704.
        if (expectedValueType.isAnyOrUnknown) {
          return actual.fields.values.none { it.isReadOnly }
        }
        if (hasExtraItems && !actual.isClosed) {
          return actual.fields.values.all { field ->
            !field.isReadOnly &&
            field.qualifiers.isRequired != true &&
            PyTypeChecker.match(expectedValueType, field.type, context) &&
            PyTypeChecker.match(field.type, expectedValueType, context)
          }
        }
        return false
      }
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

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyTypedDictType(this)
    }
    return visitor.visitPyClassType(this)
  }

}
