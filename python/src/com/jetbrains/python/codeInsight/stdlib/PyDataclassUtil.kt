/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext


const val DATACLASSES_INITVAR_TYPE = "dataclasses.InitVar"
const val DUNDER_POST_INIT = "__post_init__"


fun parseDataclassParameters(cls: PyClass, context: TypeEvalContext): DataclassParameters? {
  val decorators = cls.decoratorList ?: return null

  for (decorator in decorators.decorators) {
    if (PyKnownDecoratorUtil.asKnownDecorators(decorator, context).contains(PyKnownDecoratorUtil.KnownDecorator.DATACLASSES_DATACLASS)) {
      for (mapping in decorator.multiMapArguments(PyResolveContext.noImplicits().withTypeEvalContext(context))) {
        if (mapping.unmappedArguments.isEmpty() && mapping.unmappedParameters.isEmpty()) {
          val builder = DataclassParametersBuilder()

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

  return null
}

data class DataclassParameters(val init: Boolean,
                               val repr: Boolean,
                               val eq: Boolean,
                               val order: Boolean,
                               val hash: Boolean?,
                               val frozen: Boolean,
                               val initArgument: PyExpression?,
                               val reprArgument: PyExpression?,
                               val eqArgument: PyExpression?,
                               val orderArgument: PyExpression?,
                               val hashArgument: PyExpression?,
                               val frozenArgument: PyExpression?)

private class DataclassParametersBuilder {

  companion object {
    private val DEFAULT_INIT: Boolean = true
    private val DEFAULT_REPR: Boolean = true
    private val DEFAULT_EQ: Boolean = true
    private val DEFAULT_ORDER: Boolean = false
    private val DEFAULT_HASH: Boolean? = null // `null` means `None`
    private val DEFAULT_FROZEN: Boolean = false
  }

  private var init = DEFAULT_INIT
  private var repr = DEFAULT_REPR
  private var eq = DEFAULT_EQ
  private var order = DEFAULT_ORDER
  private var hash = DEFAULT_HASH
  private var frozen = DEFAULT_FROZEN

  private var initArgument: PyExpression? = null
  private var reprArgument: PyExpression? = null
  private var eqArgument: PyExpression? = null
  private var orderArgument: PyExpression? = null
  private var hashArgument: PyExpression? = null
  private var frozenArgument: PyExpression? = null

  fun update(name: String?, argument: PyExpression?) {
    val value = PyUtil.peelArgument(argument)

    when (name) {
      "init" -> {
        init = evaluateBoolean(value, DEFAULT_INIT)
        initArgument = argument
      }
      "repr" -> {
        repr = evaluateBoolean(value, DEFAULT_REPR)
        reprArgument = argument
      }
      "eq" -> {
        eq = evaluateBoolean(value, DEFAULT_EQ)
        eqArgument = argument
      }
      "order" -> {
        order = evaluateBoolean(value, DEFAULT_ORDER)
        orderArgument = argument
      }
      "hash" -> {
        hash = evaluateBoolean(value, DEFAULT_HASH)
        hashArgument = argument
      }
      "frozen" -> {
        frozen = evaluateBoolean(value, DEFAULT_FROZEN)
        frozenArgument = argument
      }
    }
  }

  fun build() = DataclassParameters(init, repr, eq, order, hash, frozen,
                                    initArgument, reprArgument, eqArgument, orderArgument, hashArgument, frozenArgument)


  private inline fun <reified T: Boolean?> evaluateBoolean(expression: PyExpression?, default: T): T {
    val result = PyEvaluator().evaluate(expression)
    if (result is Boolean) return result as T
    return default
  }
}