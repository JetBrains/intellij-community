/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext


const val DATACLASSES_INITVAR_TYPE: String = "dataclasses.InitVar"
const val DUNDER_POST_INIT: String = "__post_init__"


fun parseStdDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(cls, context, mapOf(KnownDecorator.DATACLASSES_DATACLASS to PyDataclassParameters.Type.STD))
}

fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): PyDataclassParameters? {
  return parseDataclassParameters(
    cls,
    context,
    mapOf(
      KnownDecorator.DATACLASSES_DATACLASS to PyDataclassParameters.Type.STD,
      KnownDecorator.ATTR_S to PyDataclassParameters.Type.ATTRS,
      KnownDecorator.ATTR_ATTRS to PyDataclassParameters.Type.ATTRS,
      KnownDecorator.ATTR_ATTRIBUTES to PyDataclassParameters.Type.ATTRS,
      KnownDecorator.ATTR_DATACLASS to PyDataclassParameters.Type.ATTRS
    )
  )
}

private fun parseDataclassParameters(cls: PyClass,
                                     context: TypeEvalContext,
                                     types: Map<KnownDecorator, PyDataclassParameters.Type>): PyDataclassParameters? {
  val decorators = cls.decoratorList ?: return null

  for (decorator in decorators.decorators) {
    for (knownDecorator in PyKnownDecoratorUtil.asKnownDecorators(decorator, context)) {
      val type = types[knownDecorator]
      if (type != null) {
        for (mapping in decorator.multiMapArguments(PyResolveContext.noImplicits().withTypeEvalContext(context))) {
          if (mapping.unmappedArguments.isEmpty() && mapping.unmappedParameters.isEmpty()) {
            val builder = PyDataclassParametersBuilder(type)

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
    }
  }

  return null
}


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

private class PyDataclassParametersBuilder(private val type: PyDataclassParameters.Type) {

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
  private var order = DEFAULT_ORDER
  private var unsafeHash = DEFAULT_UNSAFE_HASH
  private var frozen = DEFAULT_FROZEN

  private var initArgument: PyExpression? = null
  private var reprArgument: PyExpression? = null
  private var eqArgument: PyExpression? = null
  private var orderArgument: PyExpression? = null
  private var unsafeHashArgument: PyExpression? = null
  private var frozenArgument: PyExpression? = null

  private val others = mutableMapOf<String, PyExpression>()

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

    if (type == PyDataclassParameters.Type.STD) {
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
          unsafeHash = PyEvaluator.evaluateAsBoolean(value, DEFAULT_UNSAFE_HASH)
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
                                      initArgument, reprArgument, eqArgument, orderArgument, unsafeHashArgument, frozenArgument,
                                      type, others)
}