// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.InitFieldsAccumulator
import com.jetbrains.python.codeInsight.PyDataclassCopyFunction
import com.jetbrains.python.codeInsight.PyDataclassField
import com.jetbrains.python.codeInsight.PyDataclassFieldParameters
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus

interface PyDataclassResolver {
  fun resolveClassParameters(
    pyClass: PyClass,
    stub: PyDataclassStub,
    type: PyDataclassParameters.Type,
    argumentMapping: DataclassParameterArgumentMapping?,
    context: TypeEvalContext,
  ): PyDataclassParameters?

  /**
   * Qualified names whose reference as a field's assigned value or as a field-call default means "this field has no
   * default". Empty when the framework has no such sentinel.
   */
  val omittedDefaultQualifiedNames: Set<String>
    get() = emptySet()

  fun resolveFieldParameters(
    dataclass: PyClass,
    parameters: PyDataclassParameters,
    field: PyTargetExpression,
    context: TypeEvalContext,
  ): PyDataclassFieldParameters? {
    assert(field.containingClass == dataclass)

    if (isAssignedOmittedDefault(dataclass, field, context)) {
      return PyDataclassFieldParameters(
        hasDefault = false,
        hasDefaultFactory = false,
        initValue = parameters.init,
        kwOnly = parameters.kwOnly,
        parameterAlias = null,
      )
    }

    return buildFieldParametersFromStub(dataclass, parameters, field, retrieveFieldStub(field), context)
  }

  /**
   * Builds the [PyDataclassFieldParameters] once the field's [fieldStub] has been retrieved. Default = the stub-only
   * resolution shared by stdlib, attrs and third-party dataclasses (never Pydantic models, so only the explicit alias is
   * considered). [PyDataclassTransformResolver] overrides this to resolve the field-specifier callable.
   */
  fun buildFieldParametersFromStub(
    dataclass: PyClass,
    parameters: PyDataclassParameters,
    field: PyTargetExpression,
    fieldStub: PyDataclassFieldStub?,
    context: TypeEvalContext,
  ): PyDataclassFieldParameters? {
    return fieldStub?.let {
      PyDataclassFieldParameters(
        hasDefault = fieldStub.hasDefault(),
        hasDefaultFactory = fieldStub.hasDefaultFactory(),
        initValue = fieldStub.initValue(),
        kwOnly = fieldStub.kwOnly() ?: false,
        parameterAlias = fieldStub.alias,
      )
    }
  }

  /**
   * Name of the generated `__init__` parameter for [fieldName]. A per-framework hook because the mapping from field name
   * to parameter name differs by framework: stdlib uses the field name verbatim, attrs strips a single leading
   * underscore, and dataclass_transform/Pydantic use the resolved [PyDataclassFieldParameters.parameterAlias].
   */
  fun getInitParameterName(fieldName: String, fieldParams: PyDataclassFieldParameters?): String = fieldName

  /** Type of the generated `__init__` parameter for [field]. */
  fun getInitParameterType(cls: PyClass, field: PyTargetExpression, context: TypeEvalContext): PyType? {
    val type = context.getType(field)
    if (type is PyClassType &&
        type.isParameterized &&
        type.classQName == Dataclasses.DATACLASSES_INITVAR) {
      return type.typeArguments.firstOrNull()
    }
    if (type is PyClassLikeType) {
      val expected = PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(field, type, context)
      if (expected != null) return Ref.deref(expected)
    }
    return type
  }

  /** Default value (or `...` placeholder) of the generated `__init__` parameter, or `null` if required. */
  fun getInitParameterDefault(
    cls: PyClass,
    field: PyTargetExpression,
    fieldParams: PyDataclassFieldParameters?,
    context: TypeEvalContext,
  ): PyExpression? {
    val elementGenerator = PyElementGenerator.getInstance(cls.project)
    val ellipsis = elementGenerator.createEllipsis()

    return if (fieldParams == null) {
      when {
        context.maySwitchToAST(field) -> field.findAssignedValue()
        field.hasAssignedValue() -> ellipsis
        else -> null
      }
    }
    else if (fieldParams.hasDefault || fieldParams.hasDefaultFactory) ellipsis else null
  }

  /** Places one field's parameter into [acc]; overridden by attrs to change ordering. */
  fun placeFieldInInitSignature(
    acc: InitFieldsAccumulator,
    fieldInfo: PyDataclassField,
    index: Int,
    indexOfKeywordOnlyAttribute: Int,
    context: TypeEvalContext,
  ) {
    val aliasOrFieldName = fieldInfo.parameterName
    val kwOnly = fieldInfo.kwOnly
    val parameter = fieldInfo.parameter
    val fieldName = fieldInfo.name

    if ((acc.seenKeywordOnlyClass && kwOnly != false || index < indexOfKeywordOnlyAttribute || kwOnly == true)
        && aliasOrFieldName !in acc.positionalAliasOrFieldNameParams) {
      acc.keywordOnlyAliasOrFieldNameParams += aliasOrFieldName
      acc.keywordOnlyFieldNameParams += fieldName
    }

    if (parameter == null) {
      acc.seenNames.add(aliasOrFieldName)
    }
    else if (!isKwOnlyMarkerField(parameter, context)) {
      acc.positionalAliasOrFieldNameParams[aliasOrFieldName] =
        acc.positionalAliasOrFieldNameParams.remove(aliasOrFieldName) ?: parameter

      val fieldNameParam = PyCallableParameterImpl.nonPsi(fieldName, parameter.getType(context), parameter.defaultValue)
      acc.positionalFieldNameParams[fieldName] =
        acc.positionalFieldNameParams.remove(fieldName) ?: fieldNameParam
    }
  }

  /** Default: single signature. Pydantic overrides to also expose the by-field-name signature. */
  fun buildInitSignatureParameterSets(acc: InitFieldsAccumulator): List<List<PyCallableParameter>> =
    listOf(buildParameters(acc.positionalAliasOrFieldNameParams, acc.keywordOnlyAliasOrFieldNameParams))

  /** Free functions like `dataclasses.replace(obj, ...)` / `attrs.evolve(inst, ...)`. Empty if none. */
  fun copyFunctions(): List<PyDataclassCopyFunction> = emptyList()

  /** Name of the post-init hook Python calls after the generated `__init__` (e.g. stdlib `__post_init__`), or `null`. */
  fun postInitFunctionName(): String? = null

  fun applyCustomDecoratorParameter(
    accumulator: ParametersAccumulator,
    name: String,
    value: PyExpression?,
    argument: PyExpression?,
  ): Boolean {
    when (name) {
      "eq" -> {
        accumulator.eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.eqArgument = argument
      }
      "order" -> {
        accumulator.order = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.orderArgument = argument
      }
      "unsafe_hash" -> {
        accumulator.unsafeHash = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.unsafeHashArgument = argument
      }
      else -> return false
    }
    return true
  }
}

/**
 * Contributes the dataclass fields declared *directly on* [pyClass] (no MRO traversal) to [acc], using this
 * framework's rules for parameter name, type, default value and positional/keyword placement.
 *
 * Never recurses into superclasses — the MRO walk is orchestrated by [collectDataclassInitFields], which calls this
 * once per ancestor. Frameworks customise the per-field primitives ([PyDataclassResolver.getInitParameterName],
 * [PyDataclassResolver.getInitParameterType], [PyDataclassResolver.getInitParameterDefault],
 * [PyDataclassResolver.placeFieldInInitSignature]) rather than overriding the loop itself.
 */
private fun PyDataclassResolver.collectDirectInitFields(
  acc: InitFieldsAccumulator,
  pyClass: PyClass,
  parameters: PyDataclassParameters,
  context: TypeEvalContext,
) {
  val fields = pyClass.classAttributes
    .asReversed()
    .asSequence()
    .filterNot { PyTypingTypeProvider.isClassVar(it, context) }
    .mapNotNull { createDataclassFieldFromClassAttribute(it, pyClass, parameters, context) }
    .filterNot { it.parameterName in acc.seenNames }
    .toList()

  val kwOnlyMarkerIndex = fields.indexOfLast { it.parameter != null && isKwOnlyMarkerField(it.parameter, context) }

  fields.forEachIndexed { index, field ->
    // note: attributes are visited from inheritors to ancestors, in reversed order for every of them
    placeFieldInInitSignature(acc, field, index, kwOnlyMarkerIndex, context)
  }
}

/** Builds a single field declared on [cls], or `null` when it is not a generated `__init__` parameter. */
private fun PyDataclassResolver.createDataclassFieldFromClassAttribute(
  field: PyTargetExpression,
  cls: PyClass,
  parameters: PyDataclassParameters,
  context: TypeEvalContext,
): PyDataclassField? {
  val fieldName = field.name ?: return null

  val fieldParams: PyDataclassFieldParameters? = resolveFieldParameters(cls, parameters, field, context)
  if (fieldParams != null && !fieldParams.initValue) {
    return PyDataclassField(field, fieldName, fieldName, kwOnly = false, parameter = null)
  }
  if (fieldParams == null && field.annotationValue == null) return null // skip fields that are not annotated

  // The alias precedence (Pydantic validation_alias > Field(alias=...) > field name) is resolved per framework in
  // buildFieldParameters and exposed as PyDataclassFieldParameters.parameterAlias; parameterName applies the
  // per-framework parameter-name rules (e.g. attrs leading-underscore stripping) on top of it.
  val parameterName = getInitParameterName(fieldName, fieldParams)

  val parameter = PyCallableParameterImpl.nonPsi(
    parameterName,
    getInitParameterType(cls, field, context),
    getInitParameterDefault(cls, field, fieldParams, context),
    field
  )

  return PyDataclassField(field, fieldName, parameterName, fieldParams?.kwOnly, parameter)
}

private fun PyDataclassResolver.isAssignedOmittedDefault(
  dataclass: PyClass,
  field: PyTargetExpression,
  context: TypeEvalContext,
): Boolean {
  if (omittedDefaultQualifiedNames.isEmpty()) return false
  val assignedQName = field.assignedQName ?: return false

  return PyResolveUtil.resolveQualifiedNameInScope(assignedQName, ScopeUtil.getScopeOwner(dataclass)!!, context)
    .filterIsInstance<PyQualifiedNameOwner>()
    .mapNotNull { it.qualifiedName }
    .any { it in omittedDefaultQualifiedNames }
}

/**
 * The field's custom stub, framework-agnostic: field stubs are built context-free by the
 * [com.jetbrains.python.codeInsight.PyDataclassParametersProvider] extensions.
 */
private fun retrieveFieldStub(field: PyTargetExpression): PyDataclassFieldStub? =
  if (field.stub != null) {
    // Reuse the already-built PSI stub when the field is stubbed; otherwise build the field stub from AST.
    field.stub.getCustomStub(PyDataclassFieldStub::class.java)
  }
  else {
    PyDataclassFieldStubImpl.create(field)
  }

@ApiStatus.Internal
fun isKwOnlyMarkerField(parameter: PyCallableParameter, context: TypeEvalContext): Boolean {
  val psi = parameter.declarationElement
  if (psi !is PyTargetExpression) return false
  val typeHint = PyTypingTypeProvider.getAnnotationValue(psi, context) as? PyReferenceExpression ?: return false
  val type = Ref.deref(PyTypingTypeProvider.getType(typeHint, context))
  return type is PyClassType && type.classQName == Dataclasses.DATACLASSES_KW_ONLY
}

@ApiStatus.Internal
fun buildParameters(
  fields: Map<String, PyCallableParameter>,
  keywordOnly: Set<String>,
): List<PyCallableParameter> {
  if (keywordOnly.isEmpty()) return fields.values.reversed()

  val positionalOrKeyword = mutableListOf<PyCallableParameter>()
  val keyword = mutableListOf<PyCallableParameter>()

  for ((name, value) in fields.entries.reversed()) {
    if (name !in keywordOnly) {
      positionalOrKeyword += value
    }
    else {
      keyword += value
    }
  }

  return positionalOrKeyword + listOf(PyCallableParameterImpl.keywordOnlySeparatorNonPsi()) + keyword
}

/**
 * Shared, framework-agnostic MRO traversal collecting the generated `__init__` field parameters. It walks the class and
 * its ancestors, gates on `__init__`/`__new__` and the `dataclass_transform` decorator, and delegates the per-class
 * field name/type/default and positional/keyword placement to the ancestor's [collectDirectInitFields].
 * The final signature shape is produced by [PyDataclassResolver.buildInitSignatureParameterSets] of the
 * controlling framework.
 */
internal fun collectDataclassInitFields(
  clsType: PyClassType,
  context: TypeEvalContext,
  initOnly: Boolean,
): InitFieldsAccumulator? {
  val resolveContext = PyResolveContext.defaultContext(context)
  val acc = InitFieldsAccumulator()

  for (currentType in StreamEx.of<PyClassLikeType>(clsType).append(clsType.getAncestorTypes(context))) {
    if (currentType == null ||
        !currentType.resolveMember(PyNames.INIT, null, AccessDirection.READ, resolveContext, false).isNullOrEmpty() ||
        !currentType.resolveMember(PyNames.NEW, null, AccessDirection.READ, resolveContext, false).isNullOrEmpty() ||
        currentType !is PyClassType) {
      if (acc.seenInit) continue else break
    }

    val current = currentType.pyClass
    val parameters = parseDataclassParameters(current, context)

    if (parameters == null) {
      // The base class decorated with @dataclass_transform gets filtered out already here, because for it we don't detect DataclassParameters
      if (PyKnownDecoratorUtil.hasUnknownDecorator(current, context)) break else continue
    }
    else if (parameters.type.resolver == null && !parameters.type.isDataclassTransformBased) {
      break
    }

    if (acc.controllingParameters == null) acc.controllingParameters = parameters
    acc.seenInit = acc.seenInit || parameters.init
    acc.seenKeywordOnlyClass = acc.seenKeywordOnlyClass || parameters.kwOnly

    if (!initOnly || acc.seenInit) {
      parameters.type.resolver?.collectDirectInitFields(acc, current, parameters, context)
    }
  }

  if (initOnly && !acc.seenInit) return null
  return acc
}

/** All per-framework resolvers, built-in plus extension-point-contributed. Used by lookups that don't have parsed
 *  parameters yet (e.g. resolving a `dataclasses.replace` call whose target class is not known in advance). The generic
 *  third-party fallback is omitted because its copy functions duplicate the stdlib ones. */
internal val allRegisteredDataclassResolvers: List<PyDataclassResolver> =
  PyDataclassParametersProvider.EP_NAME.extensionList.mapNotNull { it.getType().resolver }
