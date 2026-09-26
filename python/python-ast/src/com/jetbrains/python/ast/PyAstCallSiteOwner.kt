package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstCallSiteOwner {
  fun getArguments(resolvedCallee: PyAstCallable?): List<PyAstExpression?>
}
