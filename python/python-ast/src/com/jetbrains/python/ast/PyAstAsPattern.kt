package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstAsPattern : PyAstPattern {
  fun getPattern(): PyAstPattern {
    return requireNotNull(findChildByClass(PyAstPattern::class.java)) { "${this}: pattern cannot be null" }
  }

  fun getTarget(): PyAstTargetExpression? {
    return findChildByClass(PyAstTargetExpression::class.java)
  }

  override fun isIrrefutable(): Boolean {
    return getPattern().isIrrefutable
  }

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor) {
    pyVisitor.visitPyAsPattern(this)
  }
}
