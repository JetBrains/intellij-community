/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight

import com.intellij.openapi.project.Project
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.PyAttrsDataclassType
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters.Type
import com.jetbrains.python.codeInsight.stdlib.PyDataclassParametersBuilder
import com.jetbrains.python.codeInsight.stdlib.PyDataclassResolver
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTransformType
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDataclassType
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyDataclassStubImpl
import com.jetbrains.python.psi.resolve.PyResolveUtil.resolveImportedElementQNameLocally
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private val DECORATOR_AND_TYPE_AND_PARAMETERS = listOf(
  Triple(PyKnownDecorator.DATACLASSES_DATACLASS, PyStdlibDataclassType, Dataclasses.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_S, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_ATTRS, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_ATTRIBUTES, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_DATACLASS, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_DEFINE, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_MUTABLE, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTR_FROZEN, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTRS_DEFINE, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTRS_MUTABLE, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(PyKnownDecorator.ATTRS_FROZEN, PyAttrsDataclassType, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
)

private val NON_PARAMETERIZED_CANDIDATE_STUB: PyDataclassStub = PyDataclassStubImpl(
  type = "DATACLASS_TRANSFORM",
  decoratorName = null,
  init = null,
  repr = null,
  eq = null,
  order = null,
  unsafeHash = null,
  frozen = null,
  matchArgs = null,
  kwOnly = null,
  slots = null,
)

fun parseStdDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf { it.type.name == PyStdlibDataclassType.name }
}

fun parseStdOrDataclassTransformDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf {
    it.type.name == PyStdlibDataclassType.name ||
    it.type.isDataclassTransformBased
  }
}

fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? =
  PyUtil.getNullableParameterizedCachedValue(cls, context) {
    StubAwareComputation.on(cls)
      .withCustomStub { stub -> stub.getCustomStub(PyDataclassStub::class.java) }
      .overStub { dataclassStub ->
        resolveDataclassParameters(cls, dataclassStub
                                        ?: NON_PARAMETERIZED_CANDIDATE_STUB, null, context)
      }
      .overAst {
        val (dataclassStub, dataclassParamArgMapping) = parseDataclassParametersFromAST(it, context)
                                                        ?: (NON_PARAMETERIZED_CANDIDATE_STUB to null)
        resolveDataclassParameters(cls, dataclassStub, dataclassParamArgMapping, context)
      }
      .withStubBuilder { PyDataclassStubImpl.create(it) }
      .compute(context)
  }

/**
 * This method MUST be used only while building stub for dataclass.
 *
 * @see parseStdDataclassParameters
 * @see parseDataclassParameters
 */
fun parseDataclassParametersForStub(cls: PyClass): PyDataclassStub? = parseDataclassParametersFromAST(cls, null)?.first

/**
 * The predefined dataclass kind [cls] belongs to (stdlib / attrs / dataclass_transform, including Pydantic), or `null`
 * if [cls] is not a dataclass in [context] or is a dataclass of a third-party framework without a predefined kind.
 *
 * Shared entry point for type providers and inspections so they don't re-derive the framework from decorators/config.
 */
fun getDataclassKind(cls: PyClass, context: TypeEvalContext): Type? =
  parseDataclassParameters(cls, context)?.type

/**
 * [expression] is the "no default" sentinel.
 * see [com.jetbrains.python.codeInsight.stdlib.PyDataclassResolver.omittedDefaultQualifiedNames]).
 */
fun PyDataclassResolver.resolvesToOmittedDefault(expression: PyExpression): Boolean =
  !(omittedDefaultQualifiedNames.isEmpty() || expression !is PyReferenceExpression) &&
  resolveImportedElementQNameLocally(expression).any { it.toString() in omittedDefaultQualifiedNames }

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
fun decoratorAndTypeAndMarkedCallee(project: Project): List<Triple<QualifiedName, Type, List<PyCallableParameter>>> {
  return PyDataclassParametersProvider.EP_NAME.extensionList.mapNotNull { it.getDecoratorAndTypeAndParameters(project) } +
         DECORATOR_AND_TYPE_AND_PARAMETERS.map {
           if (it.second.name == PyStdlibDataclassType.name) {
             val parameters = mutableListOf(PyCallableParameterImpl.keywordOnlySeparatorNonPsi())
             parameters.addAll(it.third.map { name -> PyCallableParameterImpl.nonPsi(name, PyAnyType.unknown, PyNames.ELLIPSIS) })

             Triple(it.first.qualifiedName, it.second, parameters)
           }
           else {
             Triple(it.first.qualifiedName, it.second, it.third.map { name -> PyCallableParameterImpl.nonPsi(name, PyAnyType.unknown, PyNames.ELLIPSIS) })
           }
         }
}

fun buildDecoratorDataclassStubAndMapping(
  cls: PyClass,
  context: TypeEvalContext?,
): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? {
  val decorators = cls.decoratorList ?: return null

  for (decorator in decorators.decorators) {
    val callee = (decorator.callee as? PyReferenceExpression) ?: continue

    for (decoratorQualifiedName in resolveImportedElementQNameLocally(callee)) {
      val decoratorAndTypeAndMarkedCallee = decoratorAndTypeAndMarkedCallee(cls.project).firstOrNull { it.first == decoratorQualifiedName } ?: continue

      val mapping = PyCallExpressionHelper.mapArguments(
        decorator,
        PyCallableTypeImpl(decoratorAndTypeAndMarkedCallee.third, PyAnyType.unknown),
        context ?: TypeEvalContext.codeInsightFallback(cls.project)
      )

      val builder = PyDataclassParametersBuilder(decoratorAndTypeAndMarkedCallee.second, decoratorAndTypeAndMarkedCallee.first)

      mapping
        .mappedParameters
        .entries
        .forEach {
          builder.update(it.value.name, it.key)
        }

      return builder.build()
    }
  }

  return null
}

private fun parseDataclassParametersFromAST(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? =
  PyDataclassParametersProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.buildDataclassStub(cls, context) }

/**
 * Combine immediate properties from a dataclass stub with those from its ancestors and other sources.
 */
private fun resolveDataclassParameters(
  pyClass: PyClass,
  stub: PyDataclassStub,
  argumentMapping: DataclassParameterArgumentMapping?,
  context: TypeEvalContext,
): PyDataclassParameters? {
  return PyDataclassParametersProvider.EP_NAME.extensionList
    .firstNotNullOfOrNull {
      val type = it.getType()
      type.resolver?.resolveClassParameters(pyClass, stub, type, argumentMapping, context)
    }
}

fun resolveDataclassFieldParameters(
  dataclass: PyClass,
  dataclassParams: PyDataclassParameters,
  field: PyTargetExpression,
  context: TypeEvalContext,
): PyDataclassFieldParameters? =
  dataclassParams.type.resolver?.resolveFieldParameters(dataclass, dataclassParams, field, context)

/**
 * Walks [cls] and its dataclass ancestors and returns the `InitVar[...]` pseudo-fields in declaration order, or `null`
 * when the class opts out of a generated `__init__`. `InitVar` is a stdlib-level annotation, so the detection is
 * framework-agnostic (stdlib-based frameworks such as Pydantic honor it too); frameworks that never generate an
 * `__init__` simply never see one. This is shared model construction, not per-framework behaviour, so it lives here
 * rather than on [com.jetbrains.python.codeInsight.stdlib.PyDataclassResolver].
 */
fun getDataclassInitVars(
  cls: PyClass,
  params: PyDataclassParameters?,
  context: TypeEvalContext,
): List<InitVarInfo>? {
  if (params == null || !params.init) return null
  return cls.getAncestorClasses(context)
    .asReversed()
    .asSequence()
    .filter { parseDataclassParameters(it, context) != null }
    .plus(cls)
    .flatMap { it.classAttributes }
    .mapNotNull { field ->
      val type = context.getType(field)
      if (type is PyClassType &&
          type.isParameterized &&
          type.classQName == Dataclasses.DATACLASSES_INITVAR) {
        InitVarInfo(field, type.typeArguments.singleOrNull())
      } else {
        null
      }
    }
    .toList()
}

/**
 * Data describing dataclass.
 *
 * A parameter has default value if it is omitted or its value could not be evaluated.
 * A parameter has `null` expression if it is omitted or is taken from a stub.
 */
open class PyDataclassParameters(
  val init: Boolean,
  val repr: Boolean,
  val eq: Boolean,
  val order: Boolean,
  val unsafeHash: Boolean,
  val frozen: Boolean?,
  val matchArgs: Boolean,
  val kwOnly: Boolean,
  val slots: Boolean,
  val initArgument: PyExpression?,
  val reprArgument: PyExpression?,
  val eqArgument: PyExpression?,
  val orderArgument: PyExpression?,
  val unsafeHashArgument: PyExpression?,
  val frozenArgument: PyExpression?,
  val matchArgsArgument: PyExpression?,
  val kwOnlyArgument: PyExpression?,
  val slotsArgument: PyExpression?,
  val type: Type,
  val others: Map<String, PyExpression>,
  val fieldSpecifiers: List<QualifiedName> = emptyList(),
) {

  interface Type {
    val name: String

    val resolver: PyDataclassResolver?
      get() = null

    val isDataclassTransformBased: Boolean
      get() = false

    @Deprecated("Use the concrete Type singleton (PyStdlibDataclassType, PyAttrsDataclassType, PyDataclassTransformType) instead.")
    val asPredefinedType: PredefinedType?
      get() = null
  }

  @Deprecated("Use PyStdlibDataclassType, PyAttrsDataclassType or PyDataclassTransformType instead.")
  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  enum class PredefinedType : Type {
    STD, ATTRS, DATACLASS_TRANSFORM;

    override val resolver: PyDataclassResolver?
      get() = when (this) {
        STD -> PyStdlibDataclassType.resolver
        ATTRS -> PyAttrsDataclassType.resolver
        DATACLASS_TRANSFORM -> PyDataclassTransformType.resolver
      }

    override val isDataclassTransformBased: Boolean
      get() = this == DATACLASS_TRANSFORM

    override val asPredefinedType: PredefinedType?
      get() = this
  }
}

@ApiStatus.Internal
data class DataclassParameterArgumentMapping(
  val initArgument: PyExpression?,
  val reprArgument: PyExpression?,
  val eqArgument: PyExpression?,
  val orderArgument: PyExpression?,
  val unsafeHashArgument: PyExpression?,
  val frozenArgument: PyExpression?,
  val matchArgsArgument: PyExpression?,
  val kwOnlyArgument: PyExpression?,
  val slotsArgument: PyExpression?,
  var others: Map<String, PyExpression>,
)

data class PyDataclassFieldParameters(
  val hasDefault: Boolean,
  val hasDefaultFactory: Boolean,
  val initValue: Boolean,
  val kwOnly: Boolean,
  /**
   * The alias to use as the generated `__init__` parameter name, or `null` to fall back to the field name.
   * Precedence is resolved by the owning framework's resolver;
   */
  val parameterAlias: String?,
)

/**
 * A resolved, constructor-relevant dataclass field.
 *
 * This is the shared, framework-agnostic field model: type providers, completion and inspections consume it instead of
 * each re-deriving field facts (name, alias, kw-only-ness, generated parameter) from the field's [PyDataclassFieldParameters].
 */
data class PyDataclassField(
  val declaration: PyTargetExpression,
  val name: String,
  val parameterName: String,
  val kwOnly: Boolean?,
  /** The generated constructor parameter, or `null` when the field is excluded from `__init__` (e.g. `init=False`). */
  val parameter: PyCallableParameter?,
)

class InitFieldsAccumulator {
  val positionalAliasOrFieldNameParams: LinkedHashMap<String, PyCallableParameter> = linkedMapOf()
  val keywordOnlyAliasOrFieldNameParams: LinkedHashSet<String> = linkedSetOf()
  val positionalFieldNameParams: LinkedHashMap<String, PyCallableParameter> = linkedMapOf()
  val keywordOnlyFieldNameParams: LinkedHashSet<String> = linkedSetOf()
  val seenNames: MutableSet<String> = mutableSetOf()

  var seenInit: Boolean = false
  var seenKeywordOnlyClass: Boolean = false

  /** The leaf-most dataclass in the MRO; (the leaf class for which we are building the signature). */
  var controllingParameters: PyDataclassParameters? = null
}

/** A framework-provided "shallow copy with overrides" free function (e.g. `dataclasses.replace` / `attrs.evolve`). */
data class PyDataclassCopyFunction(
  val qualifiedName: QualifiedName,
  val instanceParameterName: String,
)

/** A resolved `InitVar[...]` pseudo-field: the declaring attribute and the unwrapped init-only type. */
data class InitVarInfo(val targetExpression: PyTargetExpression, val type: PyType?)
