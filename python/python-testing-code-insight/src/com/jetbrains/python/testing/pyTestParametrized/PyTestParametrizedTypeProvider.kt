// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.openapi.util.Ref
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Fetch and provide type of params, provided by parametrized decorators
 */
class PyTestParametrizedTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? =
    param.asParametrized(context)?.type?.let { Ref(it) }

  override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: TypeEvalContext): Ref<PyType>? {
    val retval = super.getCallType(function, callSite, context)
    if (retval == null && callSite is PyCallExpression && function.qualifiedName == "_pytest.mark.param") {
      // Infer the types of positional arguments from the param() call and return them as a tuple
      val tupleClass = PyBuiltinCache.getInstance(callSite).getClass(PyNames.TUPLE) ?: return null
      val argTypes = callSite.arguments.filter { it !is PyKeywordArgument }.map { context.getType(it) }
      val tupleType = PyTupleType(tupleClass, argTypes, false)
      return Ref(tupleType)
    }
    return retval
  }
}
