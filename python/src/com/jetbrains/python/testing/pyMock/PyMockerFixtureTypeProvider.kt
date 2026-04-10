// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestFunction
import com.jetbrains.python.testing.pyTestFixtures.isFixture
import com.jetbrains.python.testing.pyTestFixtures.isPyTestEnabled

/**
 * Gives the `MockerFixture` type to the `mocker` fixtures of the `pytest-mock` package.
 *
 * The package defines its fixtures as `mocker = pytest.fixture()(_mocker)`, which the fixture resolution does not detect.
 */
internal class PyMockerFixtureTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType?>? {
    if (param.name !in fixtureNames) return null
    // An explicit annotation wins
    if (param.annotationValue != null || param.typeCommentAnnotation != null) return null
    if (!context.maySwitchToAST(func)) return null

    // pytest injects the fixture only into a test function or another fixture, in a pytest module
    val module = ModuleUtilCore.findModuleForPsiElement(func) ?: return null
    if (!isPyTestEnabled(module)) return null
    if (!isTestFunction(func) && !func.isFixture()) return null

    val mockerClass = PyPsiFacade.getInstance(func.project)
                        .createClassByQName(MOCKER_FIXTURE_FQN, func)
                      ?: return null
    return Ref(PyClassTypeImpl(mockerClass, false))
  }
}

private val fixtureNames = setOf(
  "mocker", // function
  "class_mocker",
  "module_mocker",
  "package_mocker",
  "session_mocker",
)
