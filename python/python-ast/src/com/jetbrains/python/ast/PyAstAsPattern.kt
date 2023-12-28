package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstAsPattern : PyAstPattern {
  fun getPattern(): PyAstPattern {
    return requireNotNull(findChildByClass(PyAstPattern::class.java)) { "${this}: pattern cannot be null" }
  }

  override fun isIrrefutable(): Boolean {
    return getPattern().isIrrefutable
  }
}
