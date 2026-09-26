// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Optional

/**
 * Decides where a [PyMockWithSpecType] is assignable.
 *
 * A mock with a spec (`spec=`, `spec_set=` or `wraps=`) stands in for its spec, so it is assignable where the spec is.
 * It is also an instance of its mock class. A callable mock with a class spec can stand in for that class object.
 *
 * ```python
 * a: A
 * a = MagicMock(spec=A())   # OK: the spec matches A
 * a = MagicMock(wraps=1)    # Error: the spec int does not match A
 * ```
 *
 * The extension decides only for a class or a callable as the expected type.
 * For other expected types, such as a type variable or a union, [PyTypeChecker] decides and calls the extension for the parts.
 */
internal class PyMockTypeCheckerExtension : PyTypeCheckerExtension {
  override fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Optional<Boolean> {
    if (actual !is PyMockWithSpecType) return Optional.empty()
    return when (expected) {
      is PyClassType -> Optional.of(matchClass(expected, actual, context, substitutions))
      is PyClassLikeType -> Optional.empty()
      is PyCallableType -> Optional.of(matchCallable(expected, actual, context, substitutions))
      else -> Optional.empty()
    }
  }

  private fun matchClass(
    expected: PyClassType,
    actual: PyMockWithSpecType,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Boolean {
    if (PyTypeChecker.match(expected, actual.specType, context, substitutions)) return true
    if (!expected.isDefinition) {
      // For example, `m: Mock = MagicMock(spec=A)`
      return actual.mockType.pyClass.isSubclass(expected.pyClass, context)
    }
    // For example, `factory: type[A] = MagicMock(spec=A)`. A call on the mock returns another mock.
    val specClass = actual.specType as? PyClassType ?: return false
    return actual.isCallable() && PyTypeChecker.match(expected.toInstance(), specClass, context, substitutions)
  }

  private fun matchCallable(
    expected: PyCallableType,
    actual: PyMockWithSpecType,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Boolean {
    if (!actual.isCallable()) return false
    val specCallable = actual.specType as? PyCallableType
    // An attribute that imitates a spec method keeps the signature of that method.
    if (specCallable != null && specCallable !is PyClassLikeType) {
      return PyTypeChecker.match(expected, specCallable, context, substitutions)
    }
    // A mock with a class spec accepts any arguments.
    return true
  }
}
