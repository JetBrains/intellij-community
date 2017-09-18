/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
class TerminatorTraceHandler(call: TerminatorStreamCall, dsl: Dsl) : HandlerBase.Terminal(dsl) {
  private val myPeekHandler = PeekTraceHandler(Int.MAX_VALUE, call.name, call.typeBefore, dsl.types.anyType, dsl)

  override fun additionalVariablesDeclaration(): List<VariableDeclaration> = myPeekHandler.additionalVariablesDeclaration()

  override fun prepareResult(): CodeBlock = myPeekHandler.prepareResult()

  override fun getResultExpression(): Expression = myPeekHandler.resultExpression

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()
}