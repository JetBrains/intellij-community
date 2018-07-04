// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.Lambda
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression

/**
 * @author Vitaliy.Bibaev
 */
class JavaLambda(override val variableName: String, override val body: JavaLambdaBody) : Lambda {
  override fun call(callName: String, vararg args: Expression): Expression = TextExpression("(${toCode(0)})").call(callName, *args)

  override fun toCode(indent: Int): String = "$variableName -> ${body.convert(indent)}".withIndent(indent)

  private fun JavaLambdaBody.convert(indent: Int): String =
    if (isExpression()) this.toCode(0)
    else "{\n" +
         this.toCode(indent + 1) +
         "}".withIndent(indent)
}