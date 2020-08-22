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
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyDataclassStubImpl
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.TypeEvalContext


const val DATACLASSES_INITVAR_TYPE: String = "dataclasses.InitVar"
const val DUNDER_POST_INIT: String = "__post_init__"
const val DUNDER_ATTRS_POST_INIT: String = "__attrs_post_init__"

private val STD_PARAMETERS = listOf("init", "repr", "eq", "order", "unsafe_hash", "frozen")
private val ATTRS_PARAMETERS = listOf("these", "repr_ns", "repr", "cmp", "hash", "init", "slots", "frozen", "weakref_slot", "str",
                                      "auto_attribs", "kw_only", "cache_hash", "auto_exc", "eq", "order")

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private val DECORATOR_AND_TYPE_AND_PARAMETERS = listOf(
  Triple(KnownDecorator.DATACLASSES_DATACLASS, PyDataclassParameters.PredefinedType.STD, STD_PARAMETERS),
  Triple(KnownDecorator.ATTR_S, PyDataclassParameters.PredefinedType.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRS, PyDataclassParameters.PredefinedType.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRIBUTES, PyDataclassParameters.PredefinedType.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_DATACLASS, PyDataclassParameters.PredefinedType.ATTRS, ATTRS_PARAMETERS)
)


fun parseStdDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf { it.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD }
}

fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return PyUtil.getNullableParameterizedCachedValue(cls, context) {
    StubAwareComputation.on(cls)
      .withCustomStub { stub -> stub.getCustomStub(PyDataclassStub::class.java) }
      .overStub(::parseDataclassParametersFromStub)
      .overAst { parseDataclassParametersFromAST(it, context) }
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
fun parseDataclassParametersForStub(cls: PyClass): PyDataclassParameters? = parseDataclassParametersFromAST(cls, null)

fun resolvesToOmittedDefault(expression: PyExpression, type: Type): Boolean {
  if (expression is PyReferenceExpression) {
    val qNames = PyResolveUtil.resolveImportedElementQNameLocally(expression)

    return when (type.asPredefinedType) {
      PyDataclassParameters.PredefinedType.STD -> QualifiedName.fromComponents("dataclasses", "MISSING") in qNames
      PyDataclassParameters.PredefinedType.ATTRS -> QualifiedName.fromComponents("attr", "NOTHING") in qNames
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

private fun parseDataclassParametersFromAST(cls: PyClass, context: TypeEvalContext?): PyDataclassParameters? {
  val decorators = cls.decoratorList ?: return null

  val provided = PyDataclassParametersProvider.EP_NAME.extensionList.asSequence().mapNotNull {
    it.getDataclassParameters(cls, context)
  }.firstOrNull()
  if (provided != null) return provided

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

      val builder = PyDataclassParametersBuilder(decoratorAndTypeAndMarkedCallee.second, decoratorAndTypeAndMarkedCallee.first, cls)

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

private fun parseDataclassParametersFromStub(stub: PyDataclassStub?): PyDataclassParameters? {
  return stub?.let {
    val type =
      PyDataclassParametersProvider.EP_NAME.extensionList.map { e -> e.getType() }.firstOrNull { t -> t.name == it.type }
      ?: PyDataclassParameters.PredefinedType.values().firstOrNull { t -> t.name == it.type }
      ?: PyDataclassParameters.PredefinedType.STD

    PyDataclassParameters(
      it.initValue(), it.reprValue(), it.eqValue(), it.orderValue(), it.unsafeHashValue(), it.frozenValue(), it.kwOnly(),
      null, null, null, null, null, null, null,
      type, emptyMap()
    )
  }
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
data class PyDataclassParameters(val init: Boolean,
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
                                 val others: Map<String, PyExpression>) {

  interface Type {
    val name: String
    val asPredefinedType: PredefinedType?
  }

  enum class PredefinedType : Type {
    STD, ATTRS;

    override val asPredefinedType: PredefinedType? = this
  }
}

interface PyDataclassParametersProvider {

  companion object {
    val EP_NAME: ExtensionPointName<PyDataclassParametersProvider> = ExtensionPointName.create("Pythonid.pyDataclassParametersProvider")
  }

  fun getType(): Type

  fun getDecoratorAndTypeAndParameters(project: Project): Triple<QualifiedName, Type, List<PyCallableParameter>>? = null

  fun getDataclassParameters(cls: PyClass, context: TypeEvalContext?): PyDataclassParameters? = null
}

private class PyDataclassParametersBuilder(private val type: Type, decorator: QualifiedName, anchor: PsiElement) {

  companion object {
    private const val DEFAULT_INIT = true
    private const val DEFAULT_REPR = true
    private const val DEFAULT_EQ = true
    private const val DEFAULT_ORDER = false
    private const val DEFAULT_UNSAFE_HASH = false
    private const val DEFAULT_FROZEN = false
    private const val DEFAULT_KW_ONLY = false
  }

  private var init = DEFAULT_INIT
  private var repr = DEFAULT_REPR
  private var eq = DEFAULT_EQ
  private var order = if (type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) DEFAULT_EQ else DEFAULT_ORDER
  private var unsafeHash = DEFAULT_UNSAFE_HASH
  private var frozen = DEFAULT_FROZEN
  private var kwOnly = DEFAULT_KW_ONLY

  private var initArgument: PyExpression? = null
  private var reprArgument: PyExpression? = null
  private var eqArgument: PyExpression? = null
  private var orderArgument: PyExpression? = null
  private var unsafeHashArgument: PyExpression? = null
  private var frozenArgument: PyExpression? = null
  private var kwOnlyArgument: PyExpression? = null

  private val others = mutableMapOf<String, PyExpression>()

  init {
    if (type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS && decorator == KnownDecorator.ATTR_DATACLASS.qualifiedName) {
      PyElementGenerator.getInstance(anchor.project)
        .createExpressionFromText(LanguageLevel.forElement(anchor), PyNames.TRUE)
        .also { others["auto_attribs"] = it }
    }
  }

  fun update(name: String?, argument: PyExpression?) {
    val value = PyUtil.peelArgument(argument)

    when (name) {
      "init" -> {
        init = PyEvaluator.evaluateAsBoolean(value, DEFAULT_INIT)
        initArgument = argument
        return
      }
      "repr" -> {
        repr = PyEvaluator.evaluateAsBoolean(value, DEFAULT_REPR)
        reprArgument = argument
        return
      }
      "frozen" -> {
        frozen = PyEvaluator.evaluateAsBoolean(value, DEFAULT_FROZEN)
        frozenArgument = argument
        return
      }
    }

    if (type.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
      when (name) {
        "eq" -> {
          eq = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
          eqArgument = argument
          return
        }
        "order" -> {
          order = PyEvaluator.evaluateAsBoolean(value, DEFAULT_ORDER)
          orderArgument = argument
          return
        }
        "unsafe_hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBoolean(value, DEFAULT_UNSAFE_HASH)
          unsafeHashArgument = argument
          return
        }
      }
    }
    else if (type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
      when (name) {
        "eq" -> {
          eq = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
          eqArgument = argument

          if (orderArgument == null) {
            order = eq
            orderArgument = eqArgument
          }
        }
        "order" -> {
          if (argument !is PyNoneLiteralExpression) {
            order = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
            orderArgument = argument
          }
        }
        "cmp" -> {
          eq = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
          eqArgument = argument

          order = eq
          orderArgument = eqArgument
          return
        }
        "hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBoolean(value, DEFAULT_UNSAFE_HASH)
          unsafeHashArgument = argument
          return
        }
        "kw_only" -> {
          kwOnly = PyEvaluator.evaluateAsBoolean(value, DEFAULT_KW_ONLY)
          kwOnlyArgument = argument
          return
        }
      }
    }

    if (name != null && argument != null) {
      others[name] = argument
    }
  }

  fun build() = PyDataclassParameters(
    init, repr, eq, order, unsafeHash, frozen, kwOnly,
    initArgument, reprArgument, eqArgument, orderArgument, unsafeHashArgument, frozenArgument, kwOnlyArgument,
    type, others
  )
}
