// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
open class PeekTraceHandler(num: Int, callName: String, private val myTypeBefore: GenericType, typeAfter: GenericType, dsl: Dsl)
  : HandlerBase.Intermediate(dsl) {
  val beforeMap = dsl.linkedMap(dsl.types.INT, myTypeBefore, "${callName}Peek${num}Before")
  val afterMap = dsl.linkedMap(dsl.types.INT, typeAfter, "${callName}Peek${num}After")
  override fun additionalVariablesDeclaration(): List<VariableDeclaration> =
    listOf(beforeMap.defaultDeclaration(), afterMap.defaultDeclaration())

  override fun prepareResult(): CodeBlock {
    return dsl.block {
      add(beforeMap.convertToArray(this, "beforeArray"))
      add(afterMap.convertToArray(this, "afterArray"))
    }
  }

  override fun getResultExpression(): Expression =
    dsl.newArray(dsl.types.ANY, TextExpression("beforeArray"), TextExpression("afterArray"))

  override fun additionalCallsBefore(): List<IntermediateStreamCall> {
    val lambda = dsl.lambda("x") {
      doReturn(beforeMap.set(dsl.currentTime(), lambdaArg))
    }.toCode()

    return listOf(dsl.createPeekCall(myTypeBefore, lambda))
  }

  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val lambda = dsl.lambda("x") {
      doReturn(afterMap.set(dsl.currentTime(), lambdaArg))
    }.toCode()

    return listOf(dsl.createPeekCall(myTypeBefore, lambda))
  }
}