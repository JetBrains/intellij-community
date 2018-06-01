package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFunctionParameter
import com.jetbrains.python.testing.PyTestFunctionParameterProvider
import icons.PythonIcons

/**
 * Test fixtures for py.test tests and other fixtures
 */
internal object PyTestFixtureAsParameterProvider : PyTestFunctionParameterProvider {
  override fun getArguments(function: PyFunction, evalContext: TypeEvalContext, module: Module) =
    getFixtures(module, function, evalContext).map { PyTestFunctionParameter(it.name, PythonIcons.Python.Function) }
}
