package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.codeInsight.PyDataclassNames
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Optional

class PyDataclassInitVarTypeCheckerExtension : PyTypeCheckerExtension {
  override fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Optional<Boolean> {
    if (expected is PyClassType && expected.classQName == PyDataclassNames.Dataclasses.DATACLASSES_INITVAR) {
      if (expected is PyCollectionType) {
        val elementType = expected.elementTypes.firstOrNull() ?: return Optional.of(true)
        return Optional.of(PyTypeChecker.match(elementType, actual, context, substitutions))
      }
      return Optional.of(true)
    }
    return Optional.empty()
  }
}
