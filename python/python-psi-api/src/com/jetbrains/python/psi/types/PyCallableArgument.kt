// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyExpression
import org.jetbrains.annotations.ApiStatus

/**
 * A call argument represented by its expression, or a positional argument represented only by its type.
 */
@ApiStatus.Internal
class PyCallableArgument {
  val expression: PyExpression?
  private val type: PyType?

  constructor(expression: PyExpression) {
    this.expression = expression
    this.type = null
  }

  constructor(type: PyType?) {
    this.expression = null
    this.type = type
  }

  fun getType(context: TypeEvalContext): PyType? {
    return if (expression != null) context.getType(expression) else type
  }
}
