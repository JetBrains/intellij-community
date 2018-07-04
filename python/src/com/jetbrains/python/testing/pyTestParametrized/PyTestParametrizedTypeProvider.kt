// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Fetch and provide type of params, provided by parametrized decorators
 */
class PyTestParametrizedTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext) =
    param.asParametrized(context)?.type?.let { Ref(it) }
}
