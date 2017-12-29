// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

/**
 * @author Vitaliy.Bibaev
 */
open class TerminatorTraceHandler(call: TerminatorStreamCall, dsl: Dsl) : HandlerBase.Terminal(dsl) {
  private val myPeekHandler = PeekTraceHandler(Int.MAX_VALUE, call.name, call.typeBefore, dsl.types.ANY, dsl)

  override fun additionalVariablesDeclaration(): List<VariableDeclaration> = myPeekHandler.additionalVariablesDeclaration()

  override fun prepareResult(): CodeBlock = myPeekHandler.prepareResult()

  override fun getResultExpression(): Expression = myPeekHandler.resultExpression

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()
}