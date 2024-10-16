/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassParameters.Type
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.impl.stubs.PyDataclassStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.stubs.PyDataclassTransformDecoratorStub
import com.jetbrains.python.psi.types.*


object PyDataclassNames {
  object Dataclasses {
    const val DATACLASSES_MISSING = "dataclasses.MISSING"
    const val DATACLASSES_INITVAR = "dataclasses.InitVar"
    const val DATACLASSES_FIELDS = "dataclasses.fields"
    const val DATACLASSES_ASDICT = "dataclasses.asdict"
    const val DATACLASSES_FIELD = "dataclasses.field"
    const val DATACLASSES_REPLACE = "dataclasses.replace"
    const val DATACLASSES_KW_ONLY = "dataclasses.KW_ONLY"
    const val DUNDER_POST_INIT = "__post_init__"
    val DECORATOR_PARAMETERS = listOf("init", "repr", "eq", "order", "unsafe_hash", "frozen", "kw_only")
    val HELPER_FUNCTIONS = setOf(DATACLASSES_FIELDS, DATACLASSES_ASDICT, "dataclasses.astuple", DATACLASSES_REPLACE)
  }

  object Attrs {
    val ATTRS_NOTHING = setOf("attr.NOTHING", "attrs.NOTHING")
    val ATTRS_FACTORY = setOf("attr.Factory", "attrs.Factory")
    val ATTRS_ASSOC = setOf("attr.assoc", "attrs.assoc")
    val ATTRS_EVOLVE = setOf("attr.evolve", "attrs.evolve")
    val ATTRS_FROZEN = setOf("attr.frozen", "attrs.frozen")
    const val DUNDER_POST_INIT = "__attrs_post_init__"
    val DECORATOR_PARAMETERS = listOf(
      "these",
      "repr_ns",
      "repr",
      "cmp",
      "hash",
      "init",
      "slots",
      "frozen",
      "weakref_slot",
      "str",
      "auto_attribs",
      "kw_only",
      "cache_hash",
      "auto_exc",
      "eq",
      "order",
    )
    val FIELD_FUNCTIONS = setOf(
      "attr.ib",
      "attr.attr",
      "attr.attrib",
      "attr.field",
      "attrs.field",
    )
    val INSTANCE_HELPER_FUNCTIONS = setOf(
      "attr.asdict",
      "attr.astuple",
      "attr.assoc",
      "attr.evolve",
      "attrs.asdict",
      "attrs.astuple",
      "attrs.assoc",
      "attrs.evolve",
    )
    val CLASS_HELPERS_FUNCTIONS = setOf(
      "attr.fields",
      "attr.fields_dict",
      "attrs.fields",
      "attrs.fields_dict",
    )
  }

  object DataclassTransform {
    const val DATACLASS_TRANSFORM_NAME = "dataclass_transform"

    val DECORATOR_OR_CLASS_PARAMETERS = setOf(
      "init",
      "eq",
      "order",
      "unsafe_hash",
      "frozen",
      "match_args",
      "kw_only",
      "slots",
    )
    
    val FIELD_SPECIFIER_PARAMETERS = setOf(
      "init",
      "default",
      "default_factory",
      "factory",
      "kw_only",
      "alias",
    )
  }
}

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private val DECORATOR_AND_TYPE_AND_PARAMETERS = listOf(
  Triple(KnownDecorator.DATACLASSES_DATACLASS, PyDataclassParameters.PredefinedType.STD, PyDataclassNames.Dataclasses.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_S, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRS, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRIBUTES, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_DATACLASS, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_DEFINE, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_MUTABLE, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTR_FROZEN, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTRS_DEFINE, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTRS_MUTABLE, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
  Triple(KnownDecorator.ATTRS_FROZEN, PyDataclassParameters.PredefinedType.ATTRS, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
)

fun parseStdDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf { it.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD }
}

fun parseStdOrDataclassTransformDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf { it.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD || 
                                                          it.type.asPredefinedType == PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM }
}

fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return PyUtil.getNullableParameterizedCachedValue(cls, context) {
    return@getNullableParameterizedCachedValue StubAwareComputation.on(cls)
      .withCustomStub { stub -> stub.getCustomStub(PyDataclassStub::class.java) }
      .overStub { dataclassStub -> resolveDataclassParameters(cls, dataclassStub ?: PyDataclassStubImpl.NON_PARAMETERIZED_DATACLASS_TRANSFORM_CANDIDATE_STUB, null, context) }
      .overAst {
        val (dataclassStub, dataclassParamArgMapping) = parseDataclassParametersFromAST(it, context)
                                                        ?: (PyDataclassStubImpl.NON_PARAMETERIZED_DATACLASS_TRANSFORM_CANDIDATE_STUB to null)
        return@overAst resolveDataclassParameters(cls, dataclassStub, dataclassParamArgMapping, context)
      }
      .withStubBuilder { PyDataclassStubImpl.create(it) }
      .compute(context)
  }
}

/**
 * This method MUST be used only while building stub for dataclass.
 *
 * @see parseStdDataclassParameters
 * @see parseDataclassParameters
 */
fun parseDataclassParametersForStub(cls: PyClass): PyDataclassStub? = parseDataclassParametersFromAST(cls, null)?.first

fun resolvesToOmittedDefault(expression: PyExpression, type: Type): Boolean {
  if (expression is PyReferenceExpression) {
    val qNames = PyResolveUtil.resolveImportedElementQNameLocally(expression)

    return when (type.asPredefinedType) {
      PyDataclassParameters.PredefinedType.STD, PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM -> 
        qNames.any { it.toString() == PyDataclassNames.Dataclasses.DATACLASSES_MISSING }
      PyDataclassParameters.PredefinedType.ATTRS -> qNames.any { it.toString() in PyDataclassNames.Attrs.ATTRS_NOTHING }
      else -> false
    }
  }

  return false
}

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private fun decoratorAndTypeAndMarkedCallee(project: Project): List<Triple<QualifiedName, Type, List<PyCallableParameter>>> {
  val generator = PyElementGenerator.getInstance(project)
  val ellipsis = generator.createEllipsis()

  return PyDataclassParametersProvider.EP_NAME.extensionList.mapNotNull { it.getDecoratorAndTypeAndParameters(project) } +
         DECORATOR_AND_TYPE_AND_PARAMETERS.map {
           if (it.second == PyDataclassParameters.PredefinedType.STD) {
             val parameters = mutableListOf(PyCallableParameterImpl.psi(generator.createSingleStarParameter()))
             parameters.addAll(it.third.map { name -> PyCallableParameterImpl.nonPsi(name, null, ellipsis) })

             Triple(it.first.qualifiedName, it.second, parameters)
           }
           else {
             Triple(it.first.qualifiedName, it.second, it.third.map { name -> PyCallableParameterImpl.nonPsi(name, null, ellipsis) })
           }
         }
}

private fun parseDataclassParametersFromAST(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? {
  val decorators = cls.decoratorList

  if (decorators != null) {
    val provided = PyDataclassParametersProvider.EP_NAME.extensionList.asSequence().mapNotNull {
      it.getDataclassParameters(cls, context)
    }.firstOrNull()
    if (provided != null) return Pair(
      PyDataclassStubImpl(
        type = provided.type.toString(),
        decoratorName = null,
        init = provided.init,
        repr = provided.repr,
        eq = provided.eq,
        order = provided.order,
        unsafeHash = provided.unsafeHash,
        frozen = provided.frozen,
        kwOnly = provided.kwOnly,
      ),
      DataclassParameterArgumentMapping(
        initArgument = provided.initArgument,
        reprArgument = provided.reprArgument,
        eqArgument = provided.eqArgument,
        orderArgument = provided.orderArgument,
        unsafeHashArgument = provided.unsafeHashArgument,
        frozenArgument = provided.frozenArgument,
        kwOnlyArgument = provided.kwOnlyArgument,
        others = provided.others,
      )
    )

    for (decorator in decorators.decorators) {
      val callee = (decorator.callee as? PyReferenceExpression) ?: continue
  
      for (decoratorQualifiedName in PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
        val types = decoratorAndTypeAndMarkedCallee(cls.project)
        val decoratorAndTypeAndMarkedCallee = types.firstOrNull { it.first == decoratorQualifiedName } ?: continue
  
        val mapping = PyCallExpressionHelper.mapArguments(
          decorator,
          PyCallableTypeImpl(decoratorAndTypeAndMarkedCallee.third, null),
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
  
      // Process decorators that have dataclass_transform-compatible keyword arguments.
      if (decorator.qualifiedName == null) continue
      val decoratorKeywordArguments = decorator.arguments.filterIsInstance<PyKeywordArgument>()
      if (decoratorKeywordArguments.map { it.name }.any { it in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }) {
        val builder = PyDataclassParametersBuilder(PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM, decorator.qualifiedName!!)
        decoratorKeywordArguments.forEach {
          builder.update(it.keyword, it)
        }
        return builder.build()
      }
    }
  }
  // Process dataclass_transform-compatible keyword argument in the list of superclasses.
  val superclassList = cls.superClassExpressionList
  if (superclassList != null) {
    val classKeywordArguments = superclassList.arguments.filterIsInstance<PyKeywordArgument>()
    if (classKeywordArguments.map { it.name }.any { it in PyDataclassNames.DataclassTransform.DECORATOR_OR_CLASS_PARAMETERS }) {
      val builder = PyDataclassParametersBuilder(PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM, null)
      classKeywordArguments.forEach {
        builder.update(it.keyword, it)
      }
      return builder.build()
    }
  }

  return null
}

/**
 * Data describing dataclass.
 *
 * A parameter has default value if it is omitted or its value could not be evaluated.
 * A parameter has `null` expression if it is omitted or is taken from a stub.
 *
 * This class also describes [type] of the dataclass and
 * contains key-value pairs of other parameters and their expressions.
 */
data class PyDataclassParameters(
  val init: Boolean,
  val repr: Boolean,
  val eq: Boolean,
  val order: Boolean,
  val unsafeHash: Boolean,
  val frozen: Boolean,
  val kwOnly: Boolean,
  val initArgument: PyExpression?,
  val reprArgument: PyExpression?,
  val eqArgument: PyExpression?,
  val orderArgument: PyExpression?,
  val unsafeHashArgument: PyExpression?,
  val frozenArgument: PyExpression?,
  val kwOnlyArgument: PyExpression?,
  val type: Type,
  val others: Map<String, PyExpression>,
  val fieldSpecifiers: List<QualifiedName> = emptyList()
) {

  interface Type {
    val name: String
    val asPredefinedType: PredefinedType?
  }

  enum class PredefinedType : Type {
    STD, ATTRS, DATACLASS_TRANSFORM;

    override val asPredefinedType: PredefinedType? = this
  }
}

@JvmDefaultWithCompatibility
interface PyDataclassParametersProvider {

  companion object {
    val EP_NAME: ExtensionPointName<PyDataclassParametersProvider> = ExtensionPointName.create("Pythonid.pyDataclassParametersProvider")
  }

  fun getType(): Type

  fun getDecoratorAndTypeAndParameters(project: Project): Triple<QualifiedName, Type, List<PyCallableParameter>>? = null

  fun getDataclassParameters(cls: PyClass, context: TypeEvalContext?): PyDataclassParameters? = null
}

private class PyDataclassParametersBuilder(private val type: Type, private val decorator: QualifiedName?) {
  private var init: Boolean? = null
  private var repr: Boolean? = null
  private var eq: Boolean? = null
  private var order: Boolean? = null
  private var unsafeHash: Boolean? = null
  private var frozen: Boolean? = null
  private var kwOnly: Boolean? = null

  private var initArgument: PyExpression? = null
  private var reprArgument: PyExpression? = null
  private var eqArgument: PyExpression? = null
  private var orderArgument: PyExpression? = null
  private var unsafeHashArgument: PyExpression? = null
  private var frozenArgument: PyExpression? = null
  private var kwOnlyArgument: PyExpression? = null

  private val others = mutableMapOf<String, PyExpression>()

  fun update(name: String?, argument: PyExpression?) {
    val value = PyUtil.peelArgument(argument)

    when (name) {
      "init" -> {
        init = PyEvaluator.evaluateAsBooleanNoResolve(value)
        initArgument = argument
        return
      }
      "repr" -> {
        repr = PyEvaluator.evaluateAsBooleanNoResolve(value)
        reprArgument = argument
        return
      }
      "frozen" -> {
        frozen = PyEvaluator.evaluateAsBooleanNoResolve(value)
        frozenArgument = argument
        return
      }
      "kw_only" -> {
        kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(value)
        kwOnlyArgument = argument
        return
      }
    }

    if (type.asPredefinedType == PyDataclassParameters.PredefinedType.STD ||
        type.asPredefinedType == PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM) {
      when (name) {
        "eq" -> {
          eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
          eqArgument = argument
          return
        }
        "order" -> {
          order = PyEvaluator.evaluateAsBooleanNoResolve(value)
          orderArgument = argument
          return
        }
        "unsafe_hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBooleanNoResolve(value)
          unsafeHashArgument = argument
          return
        }
      }
    }
    else if (type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
      when (name) {
        "eq" -> {
          eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
          eqArgument = argument

          if (orderArgument == null && eqArgument != null) {
            order = eq
            orderArgument = eqArgument
          }
          return
        }
        "order" -> {
          if (argument !is PyNoneLiteralExpression) {
            order = PyEvaluator.evaluateAsBooleanNoResolve(value)
            orderArgument = argument
          }
          return
        }
        "cmp" -> {
          eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
          eqArgument = argument

          order = eq
          orderArgument = eqArgument
          return
        }
        "hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBooleanNoResolve(value)
          unsafeHashArgument = argument
          return
        }
      }
    }

    if (name != null && argument != null) {
      others[name] = argument
    }
  }

  fun build(): Pair<PyDataclassStub, DataclassParameterArgumentMapping> =
    Pair(
      PyDataclassStubImpl(
        type = type.name,
        decoratorName = decorator,
        init = init,
        repr = repr,
        eq = eq,
        order = order,
        unsafeHash = unsafeHash,
        frozen = frozen,
        kwOnly = kwOnly,
      ),
      DataclassParameterArgumentMapping(
        initArgument = initArgument,
        reprArgument = reprArgument,
        eqArgument = eqArgument,
        orderArgument = orderArgument,
        unsafeHashArgument = unsafeHashArgument,
        frozenArgument = frozenArgument,
        kwOnlyArgument = kwOnlyArgument,
        others = others,
      )
    )
}

private data class DataclassParameterArgumentMapping(
  val initArgument: PyExpression?,
  val reprArgument: PyExpression?,
  val eqArgument: PyExpression?,
  val orderArgument: PyExpression?,
  val unsafeHashArgument: PyExpression?,
  val frozenArgument: PyExpression?,
  val kwOnlyArgument: PyExpression?,
  val others: Map<String, PyExpression>
)

@Suppress("NullableBooleanElvis")
private fun resolveDataclassParameters(
  pyClass: PyClass,
  stub: PyDataclassStub,
  argumentMapping: DataclassParameterArgumentMapping?,
  context: TypeEvalContext,
): PyDataclassParameters? {

  val type =
    PyDataclassParametersProvider.EP_NAME.extensionList.map { e -> e.getType() }.firstOrNull { t -> t.name == stub.type }
    ?: PyDataclassParameters.PredefinedType.entries.firstOrNull { t -> t.name == stub.type }
    ?: PyDataclassParameters.PredefinedType.STD
  
  when (type.asPredefinedType) {
    PyDataclassParameters.PredefinedType.STD -> {
      return PyDataclassParameters(
        init = stub.initValue() ?: true,
        repr = stub.reprValue() ?: true,
        eq = stub.eqValue() ?: true,
        order = stub.orderValue() ?: false,
        unsafeHash = stub.unsafeHashValue() ?: false,
        frozen = stub.frozenValue() ?: false,
        kwOnly = stub.kwOnly() ?: false,
        initArgument = argumentMapping?.initArgument,
        reprArgument = argumentMapping?.reprArgument,
        eqArgument = argumentMapping?.eqArgument,
        orderArgument = argumentMapping?.orderArgument,
        unsafeHashArgument = argumentMapping?.unsafeHashArgument,
        frozenArgument = argumentMapping?.frozenArgument,
        kwOnlyArgument = argumentMapping?.kwOnlyArgument,
        others = argumentMapping?.others ?: emptyMap(),
        type = type,
        fieldSpecifiers = listOf(QualifiedName.fromDottedString(PyDataclassNames.Dataclasses.DATACLASSES_FIELD)),
      )
    }
    PyDataclassParameters.PredefinedType.ATTRS -> {
      // TODO remove this hack, make it a proper field
      val extraArguments = mutableMapOf<String, PyExpression>()
      if (type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS && stub.decoratorName() == KnownDecorator.ATTR_DATACLASS.qualifiedName) {
        extraArguments["auto_attribs"] = 
          PyElementGenerator.getInstance(pyClass.project).createExpressionFromText(LanguageLevel.forElement(pyClass), PyNames.TRUE)
      }
      
      return PyDataclassParameters(
        init = stub.initValue() ?: true,
        repr = stub.reprValue() ?: true,
        eq = stub.eqValue() ?: true,
        order = stub.orderValue() ?: true,
        unsafeHash = stub.unsafeHashValue() ?: false,
        frozen = stub.frozenValue() ?: (stub.decoratorName()?.toString() in PyDataclassNames.Attrs.ATTRS_FROZEN),
        kwOnly = stub.kwOnly() ?: false,
        initArgument = argumentMapping?.initArgument,
        reprArgument = argumentMapping?.reprArgument,
        eqArgument = argumentMapping?.eqArgument,
        orderArgument = argumentMapping?.orderArgument,
        unsafeHashArgument = argumentMapping?.unsafeHashArgument,
        frozenArgument = argumentMapping?.frozenArgument,
        kwOnlyArgument = argumentMapping?.kwOnlyArgument,
        others = (argumentMapping?.others ?: emptyMap()) + extraArguments,
        type = type,
        fieldSpecifiers = PyDataclassNames.Attrs.FIELD_FUNCTIONS.map(QualifiedName::fromDottedString),
      )
    }
    PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM -> {
      val dataclassTransformTargets = (pyClass.decoratorList?.decorators.orEmpty().asSequence().flatMap { resolveDecoratorStubSafe(it, context) }
                                       + sequence { yieldAll(pyClass.getAncestorClasses(context)) }
                                       + sequence { (pyClass.getMetaClassType(true, context) as? PyClassType)?.let { yield(it.pyClass) } })
      val dataclassTransformDecorator: PyDecorator? = dataclassTransformTargets
        .filterIsInstance<PyDecoratable>()
        .flatMap { it.decoratorList?.decorators.orEmpty().asSequence() }
        .filter { it.qualifiedName?.lastComponent == "dataclass_transform" }
        .firstOrNull()

      if (dataclassTransformDecorator != null) {
        val dataclassTransformStub: PyDataclassTransformDecoratorStub? = StubAwareComputation.on(dataclassTransformDecorator)
          .withCustomStub { dtStub -> dtStub.getCustomStub(PyDataclassTransformDecoratorStub::class.java) }
          .overStub { dtStub -> dtStub }
          .withStubBuilder(PyDataclassTransformDecoratorStub::create)
          .compute(context)
        
        
        if (dataclassTransformStub != null) {
          val resolvedFieldSpecifiers = dataclassTransformStub.fieldSpecifiers
            .flatMap { PyResolveUtil.resolveQualifiedNameInScope(it, ScopeUtil.getScopeOwner(pyClass)!!, context) }
            .filterIsInstance<PyQualifiedNameOwner>()
            .mapNotNull { it.qualifiedName }
            .map { QualifiedName.fromDottedString(it) }
          return PyDataclassParameters(
            init = stub.initValue() ?: true,
            repr = stub.reprValue() ?: true,
            eq = stub.eqValue() ?: dataclassTransformStub.eqDefault,
            order = stub.orderValue() ?: dataclassTransformStub.orderDefault,
            unsafeHash = stub.unsafeHashValue() ?: true,
            frozen = stub.frozenValue() ?: dataclassTransformStub.frozenDefault,
            kwOnly = stub.kwOnly() ?: dataclassTransformStub.kwOnlyDefault,
            initArgument = argumentMapping?.initArgument,
            reprArgument = argumentMapping?.reprArgument,
            eqArgument = argumentMapping?.eqArgument,
            orderArgument = argumentMapping?.orderArgument,
            unsafeHashArgument = argumentMapping?.unsafeHashArgument,
            frozenArgument = argumentMapping?.frozenArgument,
            kwOnlyArgument = argumentMapping?.kwOnlyArgument,
            others = argumentMapping?.others ?: emptyMap(),
            type = type,
            fieldSpecifiers = resolvedFieldSpecifiers,
          )
        }
      }
      return null
    }
    else -> {
      // Non-standard dataclasses supported by third-party PyDataclassParametersProviders
      return PyDataclassParameters(
        init = stub.initValue() ?: true,
        repr = stub.reprValue() ?: true,
        eq = stub.eqValue() ?: true,
        order = stub.orderValue() ?: false,
        unsafeHash = stub.unsafeHashValue() ?: false,
        frozen = stub.frozenValue() ?: false,
        kwOnly = stub.kwOnly() ?: false,
        initArgument = argumentMapping?.initArgument,
        reprArgument = argumentMapping?.reprArgument,
        eqArgument = argumentMapping?.eqArgument,
        orderArgument = argumentMapping?.orderArgument,
        unsafeHashArgument = argumentMapping?.unsafeHashArgument,
        frozenArgument = argumentMapping?.frozenArgument,
        kwOnlyArgument = argumentMapping?.kwOnlyArgument,
        others = argumentMapping?.others ?: emptyMap(),
        type = type,
      )
    }
  }
}

private fun resolveDecoratorStubSafe(decorator: PyDecorator, context: TypeEvalContext): List<PsiElement> {
  val resolveContext = PyResolveContext.defaultContext(context)
  return StubAwareComputation.on(decorator)
           .overAst { psi -> psi.multiResolveCalleeFunction(resolveContext) as List<PsiElement> }
           .overStub { stub -> stub?.let { PyResolveUtil.resolveQualifiedNameInScope(it.qualifiedName, decorator.containingFile as ScopeOwner, context) } }
           .overAstStubLike { psi -> psi.qualifiedName?.let { PyResolveUtil.resolveQualifiedNameInScope(it, decorator.containingFile as ScopeOwner, context) } }
           .compute(context) ?: emptyList()
}

data class PyDataclassFieldParameters(
  val hasDefault: Boolean,
  val hasDefaultFactory: Boolean,
  val initValue: Boolean,
  val kwOnly: Boolean,
  val alias: String?,
)

fun resolveDataclassFieldParameters(
  dataclass: PyClass,
  dataclassParams: PyDataclassParameters,
  field: PyTargetExpression,
  context: TypeEvalContext,
): PyDataclassFieldParameters? {
  assert(field.containingClass == dataclass)
  
  val assignedQName = field.assignedQName
  if (assignedQName != null) {
    val resolvesToMissingOrNothing = PyResolveUtil.resolveQualifiedNameInScope(assignedQName, ScopeUtil.getScopeOwner(dataclass)!!, context)
      .filterIsInstance<PyQualifiedNameOwner>()
      .map { it.qualifiedName }
      .any { it == PyDataclassNames.Dataclasses.DATACLASSES_MISSING || it in PyDataclassNames.Attrs.ATTRS_NOTHING }
    if (resolvesToMissingOrNothing) {
      return PyDataclassFieldParameters(
        hasDefault = false,
        hasDefaultFactory = false,
        initValue = dataclassParams.init,
        kwOnly = dataclassParams.kwOnly,
        alias = null,
      )
    }
  }
  
  val fieldStub = if (field.stub != null) {
    // TODO access the green stub here
    field.stub.getCustomStub(PyDataclassFieldStub::class.java)
  }
  else {
    PyDataclassFieldStubImpl.create(field)
  }
  if (dataclassParams.type.asPredefinedType != PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM) {
    return fieldStub?.let {
      PyDataclassFieldParameters(
        hasDefault = fieldStub.hasDefault(),
        hasDefaultFactory = fieldStub.hasDefaultFactory(),
        initValue = fieldStub.initValue(),
        kwOnly = fieldStub.kwOnly() ?: false,
        alias = fieldStub.alias,
      )
    }
  }
  if (field.calleeName == null) return null
  val fieldSpecifierDeclaration = PyResolveUtil.resolveQualifiedNameInScope(field.calleeName!!, ScopeUtil.getScopeOwner(dataclass)!!, context)
    .filterIsInstance<PyQualifiedNameOwner>()
    .firstOrNull { 
      val qualifiedName = it.qualifiedName
      qualifiedName != null && QualifiedName.fromDottedString(qualifiedName) in dataclassParams.fieldSpecifiers 
    }
  if (fieldSpecifierDeclaration == null) return null
  val fieldSpecifierCallable = when (fieldSpecifierDeclaration) {
    is PyClass -> fieldSpecifierDeclaration.findInitOrNew(true, context)
    is PyFunction -> fieldSpecifierDeclaration
    else -> null
  }  
  if (fieldSpecifierCallable == null) return null
  return PyDataclassFieldParameters(
    hasDefault = fieldStub?.hasDefault() ?: false,
    hasDefaultFactory = fieldStub?.hasDefaultFactory() ?: false,
    // TODO Should we delegate to dataclass parameters init here?
    // TODO support overloading init with Literal types
    initValue = fieldStub?.initValue() ?: getArgumentDefault("init", fieldSpecifierCallable) ?: true,
    kwOnly = fieldStub?.kwOnly() ?: getArgumentDefault("kw_only", fieldSpecifierCallable) ?: dataclassParams.kwOnly,
    alias = fieldStub?.alias,
  )
}

private fun getArgumentDefault(paramName: String, function: PyFunction): Boolean? {
  when (function.parameterList.findParameterByName(paramName)?.defaultValueText) {
    PyNames.TRUE -> return true
    PyNames.FALSE -> return false
    else -> return null
  }
}


