// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Infers the type of parameters injected by stacked `@patch` and `@patch.object` decorators.
 *
 * Given:
 * ```python
 * @patch("A")   # outermost → last injected param
 * @patch("B")   # innermost → first injected param
 * def test(self, mock_b, mock_a): ...
 * ```
 * - `mock_b` gets type `MagicMock` (from the innermost `@patch("B")`)
 * - `mock_a` gets type `MagicMock` (from the outermost `@patch("A")`)
 *
 * If `new_callable=SomeClass` is present, the injected parameter has type `SomeClass`.
 * Decorators with `new=value` do not inject a parameter.
 */
internal class PyMockTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!context.maySwitchToAST(func)) return null

    val matchedPatch = getInjectingPatchDecorator(param, func, context) ?: return null
    return getTypeForPatch(matchedPatch, func, context)
  }

  private fun getTypeForPatch(dec: PyDecorator, anchor: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    // If new_callable= is specified, return an instance of that class
    val newCallableExpr = dec.getKeywordArgument("new_callable")
    if (newCallableExpr != null) {
      val callableType = context.getType(newCallableExpr)
      val classType = PyUtil.`as`(callableType, PyClassType::class.java)
      if (classType != null && classType.isDefinition) {
        return Ref(classType.toInstance())
      }
    }

    // Default: MagicMock
    return getMagicMockType(anchor)
  }

  private fun getMagicMockType(anchor: PyFunction): Ref<PyType>? {
    val magicMockClass = PyPsiFacade.getInstance(anchor.project)
                           .createClassByQName("unittest.mock.MagicMock", anchor)
                         ?: return null
    return Ref(PyClassTypeImpl(magicMockClass, false))
  }
}
