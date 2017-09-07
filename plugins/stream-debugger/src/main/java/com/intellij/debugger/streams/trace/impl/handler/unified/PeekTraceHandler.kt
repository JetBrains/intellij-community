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

import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class PeekTraceHandler(num: Int, callName: String, private val typeBefore: GenericType, private val typeAfter: GenericType, dsl: Dsl)
  : HandlerBase.Intermediate(dsl) {
  private val myBeforeMap = dsl.linkedMap(GenericType.INT, typeBefore, "${callName}Peek${num}Before")
  private val myAfterMap = dsl.linkedMap(GenericType.INT, typeAfter, "${callName}Peek${num}After")

  override fun getVariables(): List<VariableDeclaration> = listOf(myBeforeMap.defaultDeclaration(), myAfterMap.defaultDeclaration())

  override fun prepareResult(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getResultExpression(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun additionalCallsBefore(): MutableList<IntermediateStreamCall> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun additionalCallsAfter(): MutableList<IntermediateStreamCall> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}