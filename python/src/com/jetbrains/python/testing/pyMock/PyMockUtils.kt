// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDecoratorList
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestFunction
import com.jetbrains.python.testing.isUnitTestCaseClass
import com.jetbrains.python.testing.pyTestFixtures.isFixture

private const val MOCK_PATCH_FQN = "unittest.mock.patch"
private const val MOCK_FQN = "unittest.mock.Mock"
private const val MAGIC_MOCK_FQN = "unittest.mock.MagicMock"
private const val ASYNC_MOCK_FQN = "unittest.mock.AsyncMock"
private const val NON_CALLABLE_MOCK_FQN = "unittest.mock.NonCallableMock"
private const val NON_CALLABLE_MAGIC_MOCK_FQN = "unittest.mock.NonCallableMagicMock"

internal val MOCK_CLASS_FQNS = setOf(
  MOCK_FQN,
  MAGIC_MOCK_FQN,
  ASYNC_MOCK_FQN,
  NON_CALLABLE_MOCK_FQN,
  NON_CALLABLE_MAGIC_MOCK_FQN,
)

internal const val MOCKER_FIXTURE_FQN = "pytest_mock.plugin.MockerFixture"

/**
 * Returns the type of the mock that CPython creates for an attribute of a mock of [parentMock].
 *
 * The rules follow `NonCallableMock._get_child_mock` for a mock with a spec.
 * An attribute for an async method of the spec is an `AsyncMock`.
 * An attribute of a non-callable mock or of an `AsyncMock` is the matching callable mock.
 * Other attributes have the class of the parent.
 */
internal fun getChildMockType(parentMock: PyClassType, isAsyncMember: Boolean): PyClassType {
  val childFqn = if (isAsyncMember) ASYNC_MOCK_FQN
  else when (parentMock.classQName) {
    NON_CALLABLE_MAGIC_MOCK_FQN, ASYNC_MOCK_FQN -> MAGIC_MOCK_FQN
    NON_CALLABLE_MOCK_FQN -> MOCK_FQN
    else -> return parentMock
  }
  if (childFqn == parentMock.classQName) return parentMock
  val childClass = PyPsiFacade.getInstance(parentMock.pyClass.project).createClassByQName(childFqn, parentMock.pyClass)
                   ?: return parentMock
  return PyClassTypeImpl(childClass, false)
}

/**
 * Returns the type of the return value of a call on a mock of [mockType].
 *
 * The return value has the class of the mock. Returns `null` for an `AsyncMock`, because a call on it returns a coroutine.
 */
internal fun getReturnValueMockType(mockType: PyClassType): PyClassType? =
  if (mockType.classQName == ASYNC_MOCK_FQN) null else mockType

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch` or `MockerFixture.patch`
 * (bare `patch`, not `patch.object` or `patch.dict`).
 *
 * Handles both `@patch(...)` decorator and `with patch(...)` context manager usage,
 * as well as `mocker.patch(...)` from pytest-mock.
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

  // Check if this is a mocker.patch() call (pytest-mock)
  if (isMockerFixtureMethodCall(callExpr, "patch", context)) {
    return true
  }

  // For regular call expressions (e.g. `with patch(...)`), resolve the callee to its definition
  return PyUtil.multiResolveTopPriority(
    callee,
    PyResolveContext.defaultContext(context),
  ).filterIsInstance<PyQualifiedNameOwner>()
    .any { it.qualifiedName == MOCK_PATCH_FQN }
}

/**
 * Returns `true` if [callExpr] is a call to `mocker.<methodName>` where the qualifier
 * is a MockerFixture instance from pytest-mock.
 *
 * Detection strategy:
 * 1. The callee must be `<qualifier>.<methodName>`.
 * 2. The qualifier's type resolves to `pytest_mock.plugin.MockerFixture`.
 *    The `mocker` (and related) fixture parameters receive this type from
 *    [PyMockerFixtureTypeProvider], so a name-based fallback is not needed here.
 */
internal fun isMockerFixtureMethodCall(callExpr: PyCallExpression, methodName: String, context: TypeEvalContext): Boolean {
  val callee = callExpr.callee as? PyQualifiedExpression ?: return false
  return isMockerFixtureAttribute(callee, methodName, context)
}

/**
 * Returns `true` if [expression] is `<qualifier>.<name>`, where the type of the qualifier is `MockerFixture`.
 */
private fun isMockerFixtureAttribute(expression: PyExpression, name: String, context: TypeEvalContext): Boolean {
  val reference = expression as? PyQualifiedExpression ?: return false
  if (reference.name != name) return false
  val qualifier = reference.qualifier ?: return false
  val qualifierType = context.getType(qualifier)
  return qualifierType is PyClassType && qualifierType.pyClass.qualifiedName == MOCKER_FIXTURE_FQN
}

/**
 * Returns `true` if [callExpr] is a call to `patch.<methodName>`.
 *
 * The qualifier must resolve to `unittest.mock.patch`, or be `mocker.patch` from pytest-mock.
 */
private fun isPatchMethodCall(callExpr: PyCallExpression, methodName: String, context: TypeEvalContext): Boolean {
  val callee = callExpr.callee ?: return false
  if (callee.name != methodName) return false

  val qualifier = (callee as? PyQualifiedExpression)?.qualifier ?: return false
  return isMockerFixtureAttribute(qualifier, "patch", context) ||
         PyUtil.multiResolveTopPriority(qualifier, PyResolveContext.defaultContext(context))
           .filterIsInstance<PyQualifiedNameOwner>()
           .any { it.qualifiedName == MOCK_PATCH_FQN }
}

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.object` or `mocker.patch.object`.
 */
internal fun isPatchObjectCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean =
  isPatchMethodCall(callExpr, "object", context)

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.dict` or `mocker.patch.dict`.
 */
internal fun isPatchDictCall(callExpr: PyCallExpression, context: TypeEvalContext): Boolean =
  isPatchMethodCall(callExpr, "dict", context)

/**
 * Returns `true` if [callExpr] is a call to `unittest.mock.patch.multiple` or `mocker.patch.multiple`.
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
 * How the caller of a function with `@patch` decorators passes its own arguments.
 *
 * The patched function passes the mocks as positional arguments after the positional arguments of the caller.
 */
internal enum class PyPatchedFunctionCaller {
  /** `unittest` calls a test method, a setUp method or a tearDown method of a `TestCase` with no arguments. */
  UNITTEST,

  /** pytest calls a test or a fixture with the fixtures as keyword arguments. */
  PYTEST,

  /** Other code can give positional arguments before the mocks. */
  OTHER,
}

/**
 * The mocks that the `@patch` and `@patch.object` decorators of [function] inject, and the parameters that get them.
 *
 * The innermost decorator gives the first mock. A `@patch` on the class applies to its `test*` methods, outside the method decorators.
 * A decorator with a `new` argument injects no mock.
 * A test runner gives no positional arguments, so the mocks go to the first positional parameters after `self` or `cls`.
 * Other callers give their positional arguments first, so the mocks go to the last positional parameters without a default value.
 * If there are not sufficient such parameters, the mocks go to the first positional parameters.
 */
internal class PyPatchInjection private constructor(
  /** The decorators that inject a mock, innermost first. */
  private val decorators: List<PyDecorator>,
  private val caller: PyPatchedFunctionCaller,
  function: PyFunction,
) {
  private val namedParameters: List<PyNamedParameter> = function.parameterList.parameters
    .filterNot { it.isSelf }
    .mapNotNull { it.asNamed }
    .filterNot { it.isPositionalContainer || it.isKeywordContainer }

  /** The parameters that get a mock, mapped to the decorator that injects it. */
  private val injections: Map<PyNamedParameter, PyDecorator>

  init {
    val positional = namedParameters.filterNot { it.isKeywordOnly }
    val count = minOf(decorators.size, positional.size)
    injections = when (caller) {
      PyPatchedFunctionCaller.UNITTEST, PyPatchedFunctionCaller.PYTEST -> positional.take(count).zip(decorators)
      PyPatchedFunctionCaller.OTHER -> {
        val required = positional.takeWhile { !it.hasDefaultValue() }
        if (required.size >= decorators.size) required.takeLast(decorators.size).zip(decorators)
        else positional.take(count).zip(decorators)
      }
    }.toMap()
  }

  /** Returns the decorator that injects the mock for [param], or `null` if [param] gets no mock. */
  fun getInjectingDecorator(param: PyNamedParameter): PyDecorator? = injections[param]

  /** The number of mocks that have no positional parameter. */
  val missingParameterCount: Int
    get() = decorators.size - injections.size

  /**
   * The number of parameters that need an argument but get no mock.
   *
   * Only `unittest` gives no other arguments. pytest gives fixtures to these parameters, and other callers can give arguments.
   */
  val extraParameterCount: Int
    get() = when (caller) {
      PyPatchedFunctionCaller.UNITTEST -> namedParameters.count { !it.hasDefaultValue() && it !in injections }
      PyPatchedFunctionCaller.PYTEST, PyPatchedFunctionCaller.OTHER -> 0
    }

  companion object {
    /** Returns the injection for [function], or `null` if no decorator of [function] or of its class injects a mock. */
    fun of(function: PyFunction, context: TypeEvalContext): PyPatchInjection? {
      val containingClass = function.containingClass
      val classDecorators = if (containingClass != null && function.name?.startsWith(TEST_PREFIX) == true) {
        getInjectingDecorators(containingClass.decoratorList, context)
      }
      else emptyList()
      val decorators = getInjectingDecorators(function.decoratorList, context) + classDecorators
      if (decorators.isEmpty()) return null
      return PyPatchInjection(decorators, getCaller(function, containingClass, context), function)
    }

    /** Returns the decorators that inject a mock, innermost first. */
    private fun getInjectingDecorators(decoratorList: PyDecoratorList?, context: TypeEvalContext): List<PyDecorator> {
      val decorators = decoratorList?.decorators ?: return emptyList()
      return decorators.filter { isPatchOrPatchObjectCall(it, context) && !hasNewArgument(it, context) }.asReversed()
    }

    private fun getCaller(function: PyFunction, containingClass: PyClass?, context: TypeEvalContext): PyPatchedFunctionCaller {
      if (containingClass == null && !PyUtil.isTopLevel(function)) return PyPatchedFunctionCaller.OTHER
      if (containingClass != null && isUnitTestCaseClass(containingClass, context)) {
        return when {
          function.isFixture() -> PyPatchedFunctionCaller.PYTEST
          isTestFunction(function) || function.name in UNITTEST_LIFECYCLE_METHODS -> PyPatchedFunctionCaller.UNITTEST
          else -> PyPatchedFunctionCaller.OTHER
        }
      }
      return if (isTestFunction(function) || function.isFixture()) PyPatchedFunctionCaller.PYTEST else PyPatchedFunctionCaller.OTHER
    }
  }
}

/** The value of `unittest.mock.patch.TEST_PREFIX`: a class decorator patches only the methods with this prefix. */
private const val TEST_PREFIX = "test"

/** The `TestCase` methods that `unittest` calls with no arguments, other than the tests. */
private val UNITTEST_LIFECYCLE_METHODS = setOf("setUp", "tearDown", "asyncSetUp", "asyncTearDown", "setUpClass", "tearDownClass")

/**
 * Returns the `@patch` or `@patch.object` decorator that injects the mock for [param],
 * or `null` if no decorator of [func] or of its class gives a mock to [param].
 */
internal fun getInjectingPatchDecorator(
  param: PyNamedParameter,
  func: PyFunction,
  context: TypeEvalContext,
): PyDecorator? = PyPatchInjection.of(func, context)?.getInjectingDecorator(param)
