// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.Optional

@ApiStatus.Internal
class PyDataclassInitVarTypeCheckerExtension : PyTypeCheckerExtension {
  override fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Optional<Boolean> {
    if (expected is PyClassType && expected.classQName == PyDataclassNames.Dataclasses.DATACLASSES_INITVAR) {
      if (expected.isParameterized) {
        val elementType = expected.typeArguments.firstOrNull() ?: return Optional.of(true)
        return Optional.of(PyTypeChecker.match(elementType, actual, context, substitutions))
      }
      return Optional.of(true)
    }
    return Optional.empty()
  }
}
