// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassParameters.Type
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.stubs.PyDataclassStubImpl
import com.jetbrains.python.psi.stubs.PyDataclassStub
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedHashMap

@ApiStatus.Internal
class PyDataclassParametersBuilder(
  private val type: Type,
  private val decorator: QualifiedName?,
) {
  private val arguments = LinkedHashMap<String, PyExpression>()

  fun update(name: String?, argument: PyExpression?) {
    if (name != null && argument != null) {
      arguments[name] = argument
    }
  }

  fun build(): Pair<PyDataclassStub, DataclassParameterArgumentMapping> {
    val acc = ParametersAccumulator()

    for ((name, rawArgument) in arguments) {
      val value = PyUtil.peelArgument(rawArgument)

      if (type.resolver?.applyCustomDecoratorParameter(acc, name, value, rawArgument) == true) continue
      if (acc.updateCommonParameter(name, value, rawArgument)) continue

      acc.frameworkSpecificArguments[name] = rawArgument
    }

    return PyDataclassStubImpl.of(
      type = type,
      decoratorName = decorator,
      init = acc.init,
      repr = acc.repr,
      eq = acc.eq,
      order = acc.order,
      unsafeHash = acc.unsafeHash,
      frozen = acc.frozen,
      matchArgs = acc.matchArgs,
      kwOnly = acc.kwOnly,
      slots = acc.slots,
    ) to DataclassParameterArgumentMapping(
        initArgument = acc.initArgument,
        reprArgument = acc.reprArgument,
        eqArgument = acc.eqArgument,
        orderArgument = acc.orderArgument,
        unsafeHashArgument = acc.unsafeHashArgument,
        frozenArgument = acc.frozenArgument,
        matchArgsArgument = acc.matchArgsArgument,
        kwOnlyArgument = acc.kwOnlyArgument,
        slotsArgument = acc.slotsArgument,
        others = acc.frameworkSpecificArguments,
    )
  }
}

class ParametersAccumulator {
  var init: Boolean? = null
  var repr: Boolean? = null
  var eq: Boolean? = null
  var order: Boolean? = null
  var unsafeHash: Boolean? = null

  // PEP 681: A class that has been decorated with dataclass_transform is considered neither frozen nor non-frozen
  // Spec: https://typing.python.org/en/latest/spec/dataclasses.html#dataclass-semantics
  var frozen: Boolean? = null
  var matchArgs: Boolean? = null
  var kwOnly: Boolean? = null
  var slots: Boolean? = null

  var initArgument: PyExpression? = null
  var reprArgument: PyExpression? = null
  var eqArgument: PyExpression? = null
  var orderArgument: PyExpression? = null
  var unsafeHashArgument: PyExpression? = null
  var frozenArgument: PyExpression? = null
  var matchArgsArgument: PyExpression? = null
  var kwOnlyArgument: PyExpression? = null
  var slotsArgument: PyExpression? = null

  val frameworkSpecificArguments: MutableMap<String, PyExpression> = mutableMapOf()

  /** The dataclass parameters every framework shares. Returns `true` when [name] was handled. */
  fun updateCommonParameter(
    name: String,
    value: PyExpression?,
    argument: PyExpression?,
  ): Boolean {
    when (name) {
      "init" -> {
        init = PyEvaluator.evaluateAsBooleanNoResolve(value)
        initArgument = argument
      }
      "repr" -> {
        repr = PyEvaluator.evaluateAsBooleanNoResolve(value)
        reprArgument = argument
      }
      "frozen" -> {
        frozen = PyEvaluator.evaluateAsBooleanNoResolve(value)
        frozenArgument = argument
      }
      "match_args" -> {
        matchArgs = PyEvaluator.evaluateAsBooleanNoResolve(value)
        matchArgsArgument = argument
      }
      "kw_only" -> {
        kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(value)
        kwOnlyArgument = argument
      }
      "slots" -> {
        slots = PyEvaluator.evaluateAsBooleanNoResolve(value)
        slotsArgument = argument
      }
      else -> return false
    }
    return true
  }
}
