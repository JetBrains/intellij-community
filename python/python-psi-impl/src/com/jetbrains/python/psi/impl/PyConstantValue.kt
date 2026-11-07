// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger

/**
 * A Python value that [PyConstantFolder] computes with. [PyEvaluator] converts its values to this form and back.
 */
@ApiStatus.Internal
sealed interface PyConstantValue {
  /** A `str` value. */
  data class Str(val value: String) : PyConstantValue

  /** An `int` value that is not a `bool`. */
  data class Int(val value: BigInteger) : PyConstantValue

  /** A `bool` value. */
  data class Bool(val value: Boolean) : PyConstantValue
}
