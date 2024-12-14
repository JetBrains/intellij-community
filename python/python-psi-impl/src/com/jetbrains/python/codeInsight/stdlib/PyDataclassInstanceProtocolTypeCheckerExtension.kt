package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeCheckerExtension
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Optional

class PyDataclassInstanceProtocolTypeCheckerExtension : PyTypeCheckerExtension {
  override fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    substitutions: PyTypeChecker.GenericSubstitutions,
  ): Optional<Boolean> {
    if (expected is PyClassType && actual is PyClassType && expected.pyClass.name == "DataclassInstance" && isProtocol(expected, context)) {
      return Optional.of(parseDataclassParameters(actual.pyClass, context)?.type == PyDataclassParameters.PredefinedType.STD)
    }
    return Optional.empty()
  }
}