package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyFunctionArgument
import com.jetbrains.python.testing.PyFunctionArgumentProvider
import icons.PythonIcons

/**
 * Test fixtures for py.test tests and other fixtures
 */
internal object PyTestFixtureArgumentProvider : PyFunctionArgumentProvider {
  override fun getArguments(function: PyFunction, evalContext: TypeEvalContext, module: Module) =
    getFixtures(module, function, evalContext).map { PyFunctionArgument(it.name, PythonIcons.Python.Function) }
}
