package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstIfPartElif : PyAstIfPart {
  override fun isElif(): Boolean {
    return true
  }
}