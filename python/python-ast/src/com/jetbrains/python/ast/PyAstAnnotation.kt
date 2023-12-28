package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstAnnotation : PyAstElement {
  val value: PyAstExpression?
    get() = findChildByClass(PyAstExpression::class.java)
}