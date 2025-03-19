package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

/**
 * Describes a generalized expression, possibly typed.
 */
@ApiStatus.Experimental
interface PyAstExpression : PyAstTypedElement {
  companion object {
    @JvmField
    val EMPTY_ARRAY: Array<PyAstExpression> = emptyArray()
  }
}