// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.LambdaBody
import com.intellij.debugger.streams.trace.dsl.StatementFactory

/**
 * @author Vitaliy.Bibaev
 */
class JavaLambdaBody(statementFactory: StatementFactory, override val lambdaArg: Expression) : JavaCodeBlock(statementFactory), LambdaBody {
  override fun toCode(indent: Int): String = if (isExpression()) getStatements().first().toCode() else super.toCode(indent)

  fun isExpression(): Boolean = size == 1

  override fun doReturn(expression: Expression) {
    if (size == 0) {
      addStatement(expression)
    }
    else {
      super.doReturn(expression)
    }
  }
}