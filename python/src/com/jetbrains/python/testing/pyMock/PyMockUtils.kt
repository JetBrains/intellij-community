// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

private const val MOCK_PATCH_FQN = "unittest.mock.patch"

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch`
 * (bare `patch`, not `patch.object` or `patch.dict`).
 *
 * Handles both `@patch(...)` decorator and `with patch(...)` context manager usage.
 * Uses a fast callee-name check first, then resolves via PSI to confirm the FQN.
 */
internal fun isPatchCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean {
  // Fast early exit: callee name must be "patch"
  if (callExpr.callee?.name != "patch") return false

  // For decorators, use PyKnownDecoratorUtil which resolves via the stub/import chain
  if (callExpr is PyDecorator) {
    return PyKnownDecoratorUtil.asKnownDecorators(callExpr, context)
      .contains(PyKnownDecorator.UNITTEST_MOCK_PATCH)
  }

  val callee = callExpr.callee ?: return false
  // For regular call expressions (e.g. `with patch(...)`), resolve the callee to its definition
  return PyUtil.multiResolveTopPriority(
    callee,
    PyResolveContext.defaultContext(context),
  ).filterIsInstance<PyQualifiedNameOwner>()
    .any { it.qualifiedName == MOCK_PATCH_FQN }
}

/**
 * Returns `true` if [callExpr] is a call to `patch.<methodName>` where the qualifier
 * resolves to `unittest.mock.patch`.
 */
private fun isPatchMethodCall(callExpr: PyCallExpression, methodName: String, context: TypeEvalContext): Boolean {
  val callee = callExpr.callee ?: return false
  if (callee.name != methodName) return false

  val qualifier = (callee as? PyQualifiedExpression)?.qualifier ?: return false
  return PyUtil.multiResolveTopPriority(qualifier, PyResolveContext.defaultContext(context))
    .filterIsInstance<PyQualifiedNameOwner>()
    .any { it.qualifiedName == MOCK_PATCH_FQN }
}

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.object`.
 */
internal fun isPatchObjectCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean =
  isPatchMethodCall(callExpr, "object", context)

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.dict`.
 */
internal fun isPatchDictCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean =
  isPatchMethodCall(callExpr, "dict", context)

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.multiple`.
 */
internal fun isPatchMultipleCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean =
  isPatchMethodCall(callExpr, "multiple", context)

/**
 * Returns `true` if [callExpr] is a call to either `unittest.mock.patch` or `unittest.mock.patch.object`.
 */
internal fun isPatchOrPatchObjectCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean {
  return isPatchCall(callExpr, context) || isPatchObjectCall(callExpr, context)
}

/**
 * Returns `true` if the decorator call has an explicit `new` argument — either as a keyword
 * argument (`new=value`) or as the second positional argument for `patch()` (third for `patch.object()`).
 *
 * `patch(target, new, ...)` — `new` is at positional index 1.
 * `patch.object(target, attribute, new, ...)` — `new` is at positional index 2.
 */
internal fun hasNewArgument(dec: PyDecorator, context: TypeEvalContext): Boolean {
  if (dec.getKeywordArgument("new") != null) return true

  val args = dec.argumentList?.arguments ?: return false
  // Count only positional (non-keyword) arguments
  val positionalArgs = args.filter { it !is PyKeywordArgument }

  return if (isPatchCall(dec, context)) {
    // patch(target, new, ...) — new is 2nd positional arg
    positionalArgs.size >= 2
  }
  else if (isPatchObjectCall(dec, context)) {
    // patch.object(target, attribute, new, ...) — new is 3rd positional arg
    positionalArgs.size >= 3
  }
  else {
    false
  }
}

/**
 * Returns the `@patch` or `@patch.object` decorator that injects [param], or `null` if the
 * parameter is not injected by any mock decorator on [func].
 *
 * Decorators with `new` (keyword or positional) do not inject a parameter.
 * Innermost decorator injects the first parameter (after self/cls), outermost the last.
 */
internal fun getInjectingPatchDecorator(
  param: PyNamedParameter,
  func: PyFunction,
  context: TypeEvalContext,
): PyDecorator? {
  val allDecorators = func.decoratorList?.decorators ?: return null

  val injectingPatches = allDecorators.filter { dec ->
    isPatchOrPatchObjectCall(dec, context) && !hasNewArgument(dec, context)
  }

  val numPatches = injectingPatches.size
  if (numPatches == 0) return null

  val allParams = func.parameterList.parameters
  val numParams = allParams.size
  val paramIndex = allParams.indexOf(param)
  if (paramIndex < 0) return null

  // Injected params are the last numPatches parameters
  if (paramIndex < numParams - numPatches) return null

  // innermost patch (last in AST) → first injected param
  val patchIndex = numParams - paramIndex - 1
  if (patchIndex >= numPatches) return null

  return injectingPatches[patchIndex]
}
