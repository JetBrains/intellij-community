// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstCapturePattern : PyAstPattern {
  override fun isIrrefutable(): Boolean {
    return true
  }

  fun getTarget(): PyAstTargetExpression {
    return requireNotNull(findChildByClass(PyAstTargetExpression::class.java)) { "${this}: target cannot be null" }
  }

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor) {
    pyVisitor.visitPyCapturePattern(this)
  }
}
