// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassFieldParameters
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratable
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.IntentionalUnstubbing
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassTransformDecoratorStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves PEP 681 `dataclass_transform` classes. The double-check that an actual `@dataclass_transform` marker resolves
 * in the ancestry/metaclass — rejecting transform candidates that turn out not to be dataclasses — lives here.
 *
 */
@Suppress("NullableBooleanElvis")
@ApiStatus.Internal
object PyDataclassTransformResolver : PyDataclassResolver {
  // dataclasses.MISSING is not mentioned in the spec, but because dataclasses.KW_ONLY is supported,
  // this one is special-cased as well
  override val omittedDefaultQualifiedNames: Set<String> = PyDataclassNames.Dataclasses.OMITTED_DEFAULTS

  override fun resolveClassParameters(
      pyClass: PyClass,
      stub: PyDataclassStub,
      type: PyDataclassParameters.Type,
      argumentMapping: DataclassParameterArgumentMapping?,
      context: TypeEvalContext,
  ): PyDataclassParameters? {
    val dataclassTransformTargets = (pyClass.decoratorList?.decorators.orEmpty().asSequence()
                                       .flatMap { resolveDecoratorStubSafe(it, context) }
                                       .flatMap {
                                         // ResolveResult prioritisation in PyResolveUtil.resolveQualifiedNameInScope
                                         // returns only the implementation if it's present.
                                         if (it is PyFunction && !PyiUtil.isOverload(it, context)) {
                                           PyiUtil.getOverloads(it, context).asSequence() + it
                                         }
                                         else {
                                           sequenceOf(it)
                                         }
                                       }
                                     + sequence { yieldAll(pyClass.getAncestorClasses(context)) }
                                     + sequence { (pyClass.getMetaClassType(true, context) as? PyClassType)?.let { yield(it.pyClass) } })
    val dataclassTransformDecorator: PyDecorator = dataclassTransformTargets
      .filterIsInstance<PyDecoratable>()
      .flatMap { it.decoratorList?.decorators.orEmpty().asSequence() }
      .firstOrNull { it.qualifiedName?.lastComponent == PyDataclassNames.DataclassTransform.DATACLASS_TRANSFORM_NAME }
                                                  ?: return null

    val dataclassTransformStub: PyDataclassTransformDecoratorStub = StubAwareComputation.on(dataclassTransformDecorator)
      .withCustomStub { dtStub -> dtStub.getCustomStub(PyDataclassTransformDecoratorStub::class.java) }
      .overStub { dtStub -> dtStub }
      .withStubBuilder(PyDataclassTransformDecoratorStub::create)
      .compute(context)
                                                ?: return null

    val frozenValue = stub.frozenValue() ?: run {
      val isOnMetaClass = isDataclassTransformOnMetaclass(dataclassTransformDecorator, pyClass, context)

      if (isOnMetaClass) {
        dataclassTransformStub.frozenDefault
      }
      else {
        dataclassTransformStub.frozenDefault ?: false
      }
    }

    val resolvedFieldSpecifiers = dataclassTransformStub.fieldSpecifiers
      .flatMap { PyResolveUtil.resolveQualifiedNameInScope(it, ScopeUtil.getScopeOwner(dataclassTransformDecorator)!!, context) }
      .filterIsInstance<PyQualifiedNameOwner>()
      .mapNotNull { it.qualifiedName }
      .map { QualifiedName.fromDottedString(it) }
    return PyDataclassParameters(
        init = stub.initValue() ?: true,
        repr = stub.reprValue() ?: true,
        eq = stub.eqValue() ?: dataclassTransformStub.eqDefault,
        order = stub.orderValue() ?: dataclassTransformStub.orderDefault,
        unsafeHash = stub.unsafeHashValue() ?: true,
        frozen = frozenValue,
        matchArgs = stub.matchArgsValue() ?: true,
        kwOnly = stub.kwOnly() ?: dataclassTransformStub.kwOnlyDefault,
        slots = stub.slotsValue() ?: false,
        initArgument = argumentMapping?.initArgument,
        reprArgument = argumentMapping?.reprArgument,
        eqArgument = argumentMapping?.eqArgument,
        orderArgument = argumentMapping?.orderArgument,
        unsafeHashArgument = argumentMapping?.unsafeHashArgument,
        frozenArgument = argumentMapping?.frozenArgument,
        matchArgsArgument = argumentMapping?.matchArgsArgument,
        kwOnlyArgument = argumentMapping?.kwOnlyArgument,
        slotsArgument = argumentMapping?.slotsArgument,
        others = argumentMapping?.others ?: emptyMap(),
        type = type,
        fieldSpecifiers = resolvedFieldSpecifiers,
    )
  }

  override fun getInitParameterName(fieldName: String, fieldParams: PyDataclassFieldParameters?): String =
    fieldParams?.parameterAlias ?: fieldName

  /**
   * Generic `dataclass_transform` field resolution: the field's assigned value must resolve to one of the declared
   * [PyDataclassParameters.fieldSpecifiers]; `init` / `kw_only` defaults are then taken from that field-specifier
   * callable. Pydantic-specific behaviour (accepting a `Field(...)` nested in `Annotated[...]` and the Pydantic alias
   * precedence) is added by the Pydantic resolver, which reuses [resolveFieldSpecifierCallable] and
   * [buildFieldSpecifierParameters] rather than re-implementing the resolution.
   */
  override fun buildFieldParametersFromStub(
      dataclass: PyClass,
      parameters: PyDataclassParameters,
      field: PyTargetExpression,
      fieldStub: PyDataclassFieldStub?,
      context: TypeEvalContext,
  ): PyDataclassFieldParameters? {
    val fieldCalleeName = field.calleeName ?: return null
    val resolvedCallable = resolveFieldSpecifierCallable(dataclass, parameters, field, fieldCalleeName, context) ?: return null
    return buildFieldSpecifierParameters(parameters, fieldStub, resolvedCallable, parameterAlias = fieldStub?.alias)
  }

  /**
   * Resolves the field-specifier callable named by [fieldCalleeName]: the callee must resolve to one of the declared
   * [PyDataclassParameters.fieldSpecifiers], and the matching overload is selected when the specifier is overloaded.
   * Returns `null` when [fieldCalleeName] is not a declared field specifier. Shared by the generic transform path and
   * the Pydantic override.
   */
  fun resolveFieldSpecifierCallable(
      dataclass: PyClass,
      parameters: PyDataclassParameters,
      field: PyTargetExpression,
      fieldCalleeName: QualifiedName,
      context: TypeEvalContext,
  ): PyFunction? {
    val fieldSpecifierDeclaration = PyResolveUtil.resolveQualifiedNameInScope(fieldCalleeName, ScopeUtil.getScopeOwner(dataclass)!!, context)
      .filterIsInstance<PyQualifiedNameOwner>()
      .firstOrNull {
        val qualifiedName = it.qualifiedName
        qualifiedName != null && QualifiedName.fromDottedString(qualifiedName) in parameters.fieldSpecifiers
      }
    if (fieldSpecifierDeclaration == null) return null
    val fieldSpecifierCallable = when (fieldSpecifierDeclaration) {
      is PyClass -> fieldSpecifierDeclaration.findInitOrNew(true, context)
      is PyFunction -> fieldSpecifierDeclaration
      else -> null
    }
    if (fieldSpecifierCallable == null) return null

    val shouldMatchOverloads = PyiUtil.getOverloads(fieldSpecifierCallable, context).isNotEmpty()
    return if (shouldMatchOverloads) {
      val callExpression = IntentionalUnstubbing.onFileOf(field) {
        field.findAssignedValue() as? PyCallExpression
      }
      val overload = callExpression?.let { PyCallExpressionHelper.selectMatchingOverload(fieldSpecifierCallable, it, context) }
      overload ?: fieldSpecifierCallable
    }
    else {
      fieldSpecifierCallable
    }
  }

  /**
   * Builds the [PyDataclassFieldParameters] from a resolved field-specifier [resolvedCallable] and the
   * precedence-resolved [parameterAlias]. Shared by the generic transform path and the Pydantic override.
   */
  fun buildFieldSpecifierParameters(
      parameters: PyDataclassParameters,
      fieldStub: PyDataclassFieldStub?,
      resolvedCallable: PyFunction,
      parameterAlias: String?,
  ): PyDataclassFieldParameters {
    return PyDataclassFieldParameters(
        hasDefault = fieldStub?.hasDefault() ?: false,
        hasDefaultFactory = fieldStub?.hasDefaultFactory() ?: false,
        // Per PEP 681 the field specifier's own `init` default wins; fall back to `true` (there is no class-level `init`
        // override for a single field — the class `init=False` case is handled earlier, in the type provider).
        initValue = fieldStub?.initValue() ?: getArgumentDefault("init", resolvedCallable) ?: true,
        kwOnly = fieldStub?.kwOnly() ?: getArgumentDefault("kw_only", resolvedCallable) ?: parameters.kwOnly,
        parameterAlias = parameterAlias,
    )
  }
}

private fun getArgumentDefault(paramName: String, function: PyFunction): Boolean? {
  return when (function.parameterList.findParameterByName(paramName)?.defaultValueText) {
    PyNames.TRUE -> true
    PyNames.FALSE -> false
    else -> null
  }
}

/**
 * Detects a PEP 681 `dataclass_transform`-based class from core `dataclass_transform`-compatible keyword arguments on a
 * decorator or in the superclass list. This is the framework-agnostic transform detection only; Pydantic-specific
 * detection lives in the `intellij.python.pydantic` module's provider. Consumed by `parseDataclassParametersFromAST`.
 */
@ApiStatus.Internal
fun buildDataclassTransformStubAndMapping(
  cls: PyClass,
  context: TypeEvalContext?,
): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? {
  // Process decorators that have dataclass_transform-compatible keyword arguments.
  cls.decoratorList?.decorators?.forEach { decorator ->
    if (decorator.qualifiedName != null) {
      val decoratorKeywordArguments = decorator.arguments.filterIsInstance<PyKeywordArgument>()
      if (decoratorKeywordArguments.map { it.name }.any { it in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }) {
        val builder = PyDataclassParametersBuilder(PyDataclassTransformType, decorator.qualifiedName!!)
        decoratorKeywordArguments
          .filter { it.name in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }
          .forEach { builder.update(it.keyword, it) }
        return builder.build()
      }
    }
  }

  // Process dataclass_transform-compatible keyword argument in the list of superclasses.
  val superclassList = cls.superClassExpressionList
  if (superclassList != null) {
    val classKeywordArguments = superclassList.arguments.filterIsInstance<PyKeywordArgument>()
    if (classKeywordArguments.map { it.name }.any { it in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }) {
      val builder = PyDataclassParametersBuilder(PyDataclassTransformType, null)
      classKeywordArguments
        .filter { it.name in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }
        .forEach { builder.update(it.keyword, it) }
      return builder.build()
    }
  }

  return null
}

internal fun isDataclassTransformOnMetaclass(
  decorator: PyDecorator,
  pyClass: PyClass,
  context: TypeEvalContext,
): Boolean {
  val metaclass = (pyClass.getMetaClassType(true, context) as? PyClassType)?.pyClass ?: return false
  return metaclass.decoratorList?.decorators?.any { it == decorator } == true
}

@ApiStatus.Internal
fun resolveDecoratorStubSafe(decorator: PyDecorator, context: TypeEvalContext): List<PsiElement> {
  val resolveContext = PyResolveContext.defaultContext(context)
  return StubAwareComputation.on(decorator)
           .overAst { psi -> psi.multiResolveCalleeFunction(resolveContext) as List<PsiElement> }
           .overStub { stub -> stub?.let { PyResolveUtil.resolveQualifiedNameInScope(it.qualifiedName, decorator.containingFile as ScopeOwner, context) } }
           .overAstStubLike { psi -> psi.qualifiedName?.let { PyResolveUtil.resolveQualifiedNameInScope(it, decorator.containingFile as ScopeOwner, context) } }
           .compute(context) ?: emptyList()
}
