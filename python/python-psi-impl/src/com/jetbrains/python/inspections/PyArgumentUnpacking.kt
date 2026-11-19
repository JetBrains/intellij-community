// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * The call-site rules of `*` and `**` arguments that [PyArgumentListInspection] checks.
 * Each check reports only what the static types decide. An operand of unknown length or with unknown keys gives no problem.
 */
@ApiStatus.Internal
object PyArgumentUnpacking {
  /**
   * The keys that the `**` operand [operand] surely gives at runtime.
   * These are the `str` literal keys of a dict display and the required keys of a TypedDict.
   * Other keys can come too, so the result is a lower bound.
   */
  @JvmStatic
  fun definiteKeys(operand: PyExpression?, context: TypeEvalContext): Set<String> {
    val value = PyPsiUtils.flattenParens(operand) ?: return emptySet()
    if (value is PyDictLiteralExpression) {
      val keys = LinkedHashSet<String>()
      // `getElements()` gives only the key-value pairs, so read the children to see a nested `**` too.
      for (element in value.children) {
        when (element) {
          is PyKeyValueExpression -> stringLiteralValue(element.key)?.let { keys.add(it) }
          is PyDoubleStarExpression -> keys.addAll(definiteKeys(element.expression, context))
        }
      }
      return keys
    }
    val type = context.getType(value) as? PyTypedDictType ?: return emptySet()
    if (type.isDefinition) return emptySet()
    return type.fields(context).filterValues { it.isRequired }.keys
  }

  /**
   * True when [argument] is a `*` or `**` argument whose operand can be empty at runtime, so that it can map to no parameter.
   * A `*` of a tuple of known length and a `**` with a [definite key][definiteKeys] cannot be empty.
   * A display with an element that is not a `*` element cannot be empty either.
   */
  @JvmStatic
  fun canBeEmpty(argument: PyExpression, context: TypeEvalContext): Boolean {
    if (argument !is PyStarArgument) return false
    val operand = PyPsiUtils.flattenParens(argument.expression) ?: return false
    if (argument.isKeyword) {
      return definiteKeys(operand, context).isEmpty()
    }
    if (operand is PySequenceExpression) return operand.elements.all { it is PyStarExpression }
    return PyCallExpressionHelper.knownFixedTupleSpreadLength(operand, context) <= 0
  }

  /**
   * Reports a keyword that a call gives twice through a `**` argument.
   * The other source is an explicit keyword argument, another `**` argument, or a positional argument for the same parameter.
   * CPython raises "got multiple values" in each case.
   */
  @JvmStatic
  fun checkRepeatedKeywords(
    argumentList: PyArgumentList,
    mappings: List<PyCallExpression.PyArgumentsMapping>,
    holder: ProblemsHolder,
    context: TypeEvalContext,
  ) {
    val unpackedKeys = argumentList.arguments
      .filterIsInstance<PyStarArgument>()
      .filter { it.isKeyword }
      .associateWith { definiteKeys(it.expression, context) }
      .filterValues { it.isNotEmpty() }
    if (unpackedKeys.isEmpty()) return

    // Explicit keyword arguments and earlier `**` keys build the keyword dictionary of the call before the callee binds it.
    val seen = argumentList.arguments.filterIsInstance<PyKeywordArgument>().mapNotNullTo(HashSet()) { it.keyword }
    val reported = HashSet<Pair<PyStarArgument, String>>()
    for ((argument, keys) in unpackedKeys) {
      for (key in keys) {
        if (!seen.add(key) && reported.add(argument to key)) {
          registerMultipleValues(holder, argument, key)
        }
      }
    }

    val mapping = mappings.singleOrNull() ?: return
    val parameters = mapping.callableType?.getParameters(context) ?: return
    for ((argument, parameter) in mapping.mappedParameters) {
      if (argument is PyKeywordArgument || argument is PyStarArgument) continue
      if (parameter.isPositionalContainer || parameter.isKeywordContainer) continue
      val name = parameter.name ?: continue
      // A key that names a `/` parameter goes into `**kwargs`, or is an unexpected keyword without it.
      // A `__x` parameter is skipped too: in a class body, CPython mangles its name, so the key does not match it.
      if (isPositionalOnly(parameter, parameters)) continue
      for ((unpacked, keys) in unpackedKeys) {
        if (name in keys && unpacked !in mapping.unmappedArguments && reported.add(unpacked to name)) {
          registerMultipleValues(holder, unpacked, name)
        }
      }
    }
  }

  private fun registerMultipleValues(holder: ProblemsHolder, argument: PyStarArgument, name: String) {
    val message = PyPsiBundle.problemMessage("INSP.multiple.values.for.argument", name)
    holder.problem(argument, message.description).tooltip(message.tooltip).register()
  }

  private fun isPositionalOnly(parameter: PyCallableParameter, parameters: List<PyCallableParameter>): Boolean {
    val slashIndex = parameters.indexOfFirst { it.isPositionOnlySeparator }
    if (slashIndex >= 0 && parameters.indexOf(parameter) in 0 until slashIndex) return true
    val name = parameter.name ?: return true
    return name.startsWith("__") && !name.endsWith("__")
  }

  private fun stringLiteralValue(expression: PyExpression?): String? {
    val literal = PyPsiUtils.flattenParens(expression) as? PyStringLiteralExpression ?: return null
    if (literal.stringElements.any { it.isBytes || it.isFormatted }) return null
    return literal.stringValue
  }
}
