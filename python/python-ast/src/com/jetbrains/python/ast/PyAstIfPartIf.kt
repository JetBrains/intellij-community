package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstIfPartIf : PyAstIfPart {
  override fun isElif(): Boolean = false
}