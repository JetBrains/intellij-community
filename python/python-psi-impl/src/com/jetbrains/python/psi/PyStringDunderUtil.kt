// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

object PyStringDunderUtil {
  /**
   * these types don't have a `__str__` method, but they still get stringed by the interpreter
   */
  val TYPES_WITH_BUILTIN_STR: Set<String> = setOf(PyNames.FQN.INT, PyNames.FQN.BOOL)

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
