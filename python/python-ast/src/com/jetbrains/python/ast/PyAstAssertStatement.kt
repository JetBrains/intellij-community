// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import com.jetbrains.python.PythonDialectsTokenSetProvider
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
interface PyAstAssertStatement : PyAstStatement {
  val arguments: Array<out PyAstExpression>
    get() = childrenToPsi(PythonDialectsTokenSetProvider.getInstance().expressionTokens, PyAstExpression.EMPTY_ARRAY)

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor) {
    pyVisitor.visitPyAssertStatement(this)
  }
}
