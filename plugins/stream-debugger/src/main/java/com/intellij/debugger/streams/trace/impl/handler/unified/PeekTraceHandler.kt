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
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class PeekTraceHandler(num: Int, callName: String, private val myTypeBefore: GenericType, typeAfter: GenericType, dsl: Dsl)
  : HandlerBase.Intermediate(dsl) {
  val beforeMap = dsl.linkedMap(GenericType.INT, myTypeBefore, "${callName}Peek${num}Before")
  val afterMap = dsl.linkedMap(GenericType.INT, typeAfter, "${callName}Peek${num}After")

  override fun getVariables(): List<VariableDeclaration> = listOf(beforeMap.defaultDeclaration(), afterMap.defaultDeclaration())

  override fun prepareResult(): String {
    return dsl.code {
      +TextExpression(beforeMap.convertToArray(this, "beforeArray") +
                      afterMap.convertToArray(this, "afterArray"))
    }
  }

  override fun getResultExpression(): String {
    return dsl.code {
      +newArray(types.anyType, +"beforeArray", +"afterArray")
    }
  }

  override fun additionalCallsBefore(): List<IntermediateStreamCall> {
    val lambda = dsl.code {
      lambda("x") {
        +beforeMap.set(currentTime(), +argName)
      }
    }

    return listOf(dsl.createPeekCall(myTypeBefore, lambda))
  }

  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val lambda = dsl.code {
      lambda("x") {
        +afterMap.set(currentTime(), +argName)
      }
    }

    return listOf(dsl.createPeekCall(myTypeBefore, lambda))
  }
}