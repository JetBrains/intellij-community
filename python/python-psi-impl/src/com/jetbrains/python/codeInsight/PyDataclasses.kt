/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassParameters.Type
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyEvaluator
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
private val ATTRS_PARAMETERS = listOf("these", "repr_ns", "repr", "cmp", "hash", "init", "slots", "frozen", "str", "auto_attribs")

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private val DECORATOR_AND_TYPE_AND_PARAMETERS = listOf(
  Triple(KnownDecorator.DATACLASSES_DATACLASS, Type.STD, STD_PARAMETERS),
  Triple(KnownDecorator.ATTR_S, Type.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRS, Type.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_ATTRIBUTES, Type.ATTRS, ATTRS_PARAMETERS),
  Triple(KnownDecorator.ATTR_DATACLASS, Type.ATTRS, ATTRS_PARAMETERS)
)


fun parseStdDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context)?.takeIf { it.type == Type.STD }
}

fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return PyUtil.getNullableParameterizedCachedValue(cls, context) {
    val stub = cls.stub

    if (it.maySwitchToAST(cls)) {
      parseDataclassParametersFromAST(cls, it)
    }
    else {
      val dataclassStub = if (stub == null) PyDataclassStubImpl.create(cls) else stub.getCustomStub(PyDataclassStub::class.java)
      parseDataclassParametersFromStub(dataclassStub)
    }
  }
}

/**
 * This method MUST be used only while building stub for dataclass.
 *
 * @see parseStdDataclassParameters
 * @see parseDataclassParameters
 */
fun parseDataclassParametersForStub(cls: PyClass): PyDataclassParameters? = parseDataclassParametersFromAST(
  cls, null)

fun resolvesToOmittedDefault(expression: PyExpression, type: PyDataclassParameters.Type): Boolean {
  if (expression is PyReferenceExpression) {
    val qNames = PyResolveUtil.resolveImportedElementQNameLocally(expression)

    return when (type) {
      PyDataclassParameters.Type.STD -> QualifiedName.fromComponents("dataclasses", "MISSING") in qNames
      PyDataclassParameters.Type.ATTRS -> QualifiedName.fromComponents("attr", "NOTHING") in qNames
    }
  }

  return false
}

/**
 * It should be used only to map arguments to parameters and
 * determine what settings dataclass has.
 */
private fun decoratorAndTypeAndMarkedCallee(project: Project): List<Triple<PyKnownDecoratorUtil.KnownDecorator, PyDataclassParameters.Type, List<PyCallableParameter>>> {
  val generator = PyElementGenerator.getInstance(project)
  val ellipsis = generator.createEllipsis()

  return DECORATOR_AND_TYPE_AND_PARAMETERS.map {
    if (it.second == Type.STD) {
      val parameters = mutableListOf(PyCallableParameterImpl.psi(generator.createSingleStarParameter()))
      parameters.addAll(it.third.map { name -> PyCallableParameterImpl.nonPsi(name, null, ellipsis) })

      Triple(it.first, it.second, parameters)
    }
    else {
      Triple(it.first, it.second, it.third.map { name -> PyCallableParameterImpl.nonPsi(name, null, ellipsis) })
    }
  }
}

private fun parseDataclassParametersFromAST(cls: PyClass, context: TypeEvalContext?): PyDataclassParameters? {
  val decorators = cls.decoratorList ?: return null

  for (decorator in decorators.decorators) {
    val callee = (decorator.callee as? PyReferenceExpression) ?: continue

    for (decoratorQualifiedName in PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
      val types = decoratorAndTypeAndMarkedCallee(cls.project)
      val decoratorAndTypeAndMarkedCallee = types.firstOrNull { it.first.qualifiedName == decoratorQualifiedName } ?: continue

      val mapping = PyCallExpressionHelper.mapArguments(
        decorator,
        toMarkedCallee(decoratorAndTypeAndMarkedCallee.third),
        context ?: TypeEvalContext.codeInsightFallback(cls.project)
      )

      if (mapping.unmappedArguments.isEmpty() && mapping.unmappedParameters.isEmpty()) {
        val builder = PyDataclassParametersBuilder(decoratorAndTypeAndMarkedCallee.second,
                                                                                    decoratorAndTypeAndMarkedCallee.first, cls)

        mapping
          .mappedParameters
          .entries
          .forEach {
            builder.update(it.value.name, it.key)
          }

        return builder.build()
      }
    }
  }

  return null
}

private fun parseDataclassParametersFromStub(stub: PyDataclassStub?): PyDataclassParameters? {
  return stub?.let {
    PyDataclassParameters(
      it.initValue(), it.reprValue(), it.eqValue(), it.orderValue(), it.unsafeHashValue(), it.frozenValue(),
      null, null, null, null, null, null,
      Type.valueOf(it.type), emptyMap()
    )
  }
}

private fun toMarkedCallee(parameters: List<PyCallableParameter>): PyCallExpression.PyMarkedCallee {
  return PyCallExpression.PyMarkedCallee(PyCallableTypeImpl(parameters, null), null, null, 0, false, 0)
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
                                 val initArgument: PyExpression?,
                                 val reprArgument: PyExpression?,
                                 val eqArgument: PyExpression?,
                                 val orderArgument: PyExpression?,
                                 val unsafeHashArgument: PyExpression?,
                                 val frozenArgument: PyExpression?,
                                 val type: Type,
                                 val others: Map<String, PyExpression>) {

  enum class Type {
    STD, ATTRS
  }
}

private class PyDataclassParametersBuilder(private val type: PyDataclassParameters.Type,
                                           decorator: KnownDecorator,
                                           anchor: PsiElement) {

  companion object {
    private const val DEFAULT_INIT = true
    private const val DEFAULT_REPR = true
    private const val DEFAULT_EQ = true
    private const val DEFAULT_ORDER = false
    private const val DEFAULT_UNSAFE_HASH = false
    private const val DEFAULT_FROZEN = false
  }

  private var init = DEFAULT_INIT
  private var repr = DEFAULT_REPR
  private var eq = DEFAULT_EQ
  private var order = if (type == PyDataclassParameters.Type.ATTRS) DEFAULT_EQ else DEFAULT_ORDER
  private var unsafeHash = DEFAULT_UNSAFE_HASH
  private var frozen = DEFAULT_FROZEN

  private var initArgument: PyExpression? = null
  private var reprArgument: PyExpression? = null
  private var eqArgument: PyExpression? = null
  private var orderArgument: PyExpression? = null
  private var unsafeHashArgument: PyExpression? = null
  private var frozenArgument: PyExpression? = null

  private val others = mutableMapOf<String, PyExpression>()

  init {
    if (type == PyDataclassParameters.Type.ATTRS && decorator == KnownDecorator.ATTR_DATACLASS) {
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
        frozen = PyEvaluator.evaluateAsBoolean(value,
                                               DEFAULT_FROZEN)
        frozenArgument = argument
        return
      }
    }

    if (type == PyDataclassParameters.Type.STD) {
      when (name) {
        "eq" -> {
          eq = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
          eqArgument = argument
          return
        }
        "order" -> {
          order = PyEvaluator.evaluateAsBoolean(value,
                                                DEFAULT_ORDER)
          orderArgument = argument
          return
        }
        "unsafe_hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBoolean(value,
                                                     DEFAULT_UNSAFE_HASH)
          unsafeHashArgument = argument
          return
        }
      }
    }
    else if (type == PyDataclassParameters.Type.ATTRS) {
      when (name) {
        "cmp" -> {
          eq = PyEvaluator.evaluateAsBoolean(value, DEFAULT_EQ)
          eqArgument = argument

          order = eq
          orderArgument = eqArgument
          return
        }
        "hash" -> {
          unsafeHash = PyEvaluator.evaluateAsBoolean(value,
                                                     DEFAULT_UNSAFE_HASH)
          unsafeHashArgument = argument
          return
        }
      }
    }

    if (name != null && argument != null) {
      others[name] = argument
    }
  }

  fun build() = PyDataclassParameters(init, repr, eq, order, unsafeHash, frozen,
                                                                       initArgument, reprArgument, eqArgument, orderArgument,
                                                                       unsafeHashArgument, frozenArgument,
                                                                       type, others)
}
