// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFunctionParameter
import com.jetbrains.python.testing.PyTestFunctionParameterProvider

/**
 * Test fixtures for pytest tests and other fixtures
 */
internal object PyTestFixtureAsParameterProvider : PyTestFunctionParameterProvider {
  override fun getArguments(function: PyFunction, evalContext: TypeEvalContext, module: Module): List<PyTestFunctionParameter> {
    return getFixtures(module, function, evalContext)
      .map { PyTestFunctionParameter(it.name, IconManager.getInstance().getPlatformIcon(PlatformIcons.Function)) }
  }
}
