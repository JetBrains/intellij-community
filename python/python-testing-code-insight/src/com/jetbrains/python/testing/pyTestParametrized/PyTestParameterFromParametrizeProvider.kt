// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFunctionParameter
import com.jetbrains.python.testing.PyTestFunctionParameterProvider

/**
 * test decorated with parametrize must have appropriate parameter names
 */
internal object PyTestParameterFromParametrizeProvider : PyTestFunctionParameterProvider {
  override fun getArguments(function: PyFunction, evalContext: TypeEvalContext, module: Module) =
    function.getParametersOfParametrized(TypeEvalContext.codeAnalysis(function.project, function.containingFile))
      .map {
        PyTestFunctionParameter(it.name)
      }
}
