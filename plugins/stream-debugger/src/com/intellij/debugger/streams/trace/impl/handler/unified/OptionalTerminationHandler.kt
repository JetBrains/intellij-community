// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.core.trace.dsl.CodeBlock
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.dsl.Expression
import com.intellij.debugger.streams.core.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.core.trace.impl.handler.unified.HandlerBase
import com.intellij.debugger.streams.core.trace.impl.handler.unified.TerminatorTraceHandler
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes

/**
 * @author Vitaliy.Bibaev
 */
class OptionalTerminationHandler(private val call: TerminatorStreamCall, private val resultExpression: String, dsl: Dsl)
  : HandlerBase.Terminal(dsl) {
  private val myTerminatorHandler = TerminatorTraceHandler(call, dsl)
  override fun additionalVariablesDeclaration(): List<VariableDeclaration> = myTerminatorHandler.additionalVariablesDeclaration()

  override fun prepareResult(): CodeBlock = myTerminatorHandler.prepareResult()

  override fun getResultExpression(): Expression {
    val isPresent = dsl.newArray(dsl.types.BOOLEAN, TextExpression(resultExpression).call("isPresent"))
    val optionalType = JavaTypes.unwrapOptional(call.resultType)
    val optionalContent = dsl.newArray(optionalType,
                                       TextExpression(resultExpression).call("orElse", TextExpression(optionalType.defaultValue)))
    val optionalData = dsl.newArray(dsl.types.ANY, isPresent, optionalContent)
    return dsl.newArray(dsl.types.ANY, myTerminatorHandler.resultExpression, optionalData)
  }

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myTerminatorHandler.additionalCallsBefore()
}