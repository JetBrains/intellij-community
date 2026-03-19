// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Infers the type of the `mocker` parameter from pytest-mock as `MockerFixture`.
 *
 * Given:
 * ```python
 * def test_foo(mocker):
 *     mocker.patch(...)  # mocker has type MockerFixture
 * ```
 *
 * The `mocker` fixture is a reserved pytest fixture provided by pytest-mock plugin.
 * This type provider ensures that parameters named `mocker` get the correct type
 * `pytest_mock.plugin.MockerFixture`.
 */
internal class PyMockerFixtureTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (param.name != "mocker") return null
    if (!context.maySwitchToAST(func)) return null

    val mockerClass = PyPsiFacade.getInstance(func.project)
                        .createClassByQName(MOCKER_FIXTURE_FQN, func)
                      ?: return null
    return Ref(PyClassTypeImpl(mockerClass, false))
  }
}
