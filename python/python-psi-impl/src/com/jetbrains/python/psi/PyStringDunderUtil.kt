// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

object PyStringDunderUtil {
  /**
   * A string conversion of one of these types is not useful. Either the type keeps the `__str__` and the `__repr__`
   * of `object`, or its own `__repr__` prints only the name of the type and an address.
   *
   * A type stub cannot tell a type that overrides `__str__` from a type that does not, because the flake8-pyi rule
   * Y029 removes a `__str__` or a `__repr__` that only overrides the `object` one. This set gives the answer for the
   * types above, and lets an inspection report them when no runtime module and no skeleton is available.
   */
  val TYPES_WITHOUT_USEFUL_STRING_CONVERSION: Set<String> = setOf(
    "builtins.zip",
    "builtins.map",
    "builtins.filter",
    "builtins.enumerate",
    "builtins.reversed",
    PyTypingTypeProvider.GENERATOR,
    PyTypingTypeProvider.ASYNC_GENERATOR,
  )

  val KNOWN_INT_TYPES: Set<String> = setOf(PyNames.FQN.INT, "numpy.int8", "numpy.int16", "numpy.int32", "numpy.int64")
  val KNOWN_DECIMAL_TYPES: Set<String> = KNOWN_INT_TYPES + setOf(
    PyNames.FQN.FLOAT, "decimal.Decimal", "fractions.Fraction",
    "numpy.float16", "numpy.float32", "numpy.float64",
  )
  val KNOWN_COMPLEX_TYPES: Set<String> = KNOWN_DECIMAL_TYPES + setOf(
    PyNames.FQN.COMPLEX, "numpy.complex64", "numpy.complex128",
  )

  val KNOWN_FORMAT_MINI_LANGUAGE_TYPES: Set<String> = KNOWN_COMPLEX_TYPES + setOf(PyNames.FQN.STR)

  fun PyType.isAllowedFormatOverride(allowedQNames: Set<String>, context: TypeEvalContext): Boolean {
    if (this !is PyClassType) return false
    val actualQNames = this.pyClass.getAncestorTypes(context).mapTo(mutableSetOf()) { it?.classQName } + this.classQName
    return allowedQNames.any { it in actualQNames }
  }
}
