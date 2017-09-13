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
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class DistinctTraceHandler(num: Int, private val myCall: IntermediateStreamCall, dsl: Dsl) : HandlerBase.Intermediate(dsl) {
  private val myPeekTracer = PeekTraceHandler(num, "distinct", myCall.typeBefore, myCall.typeAfter, dsl)
  override fun getVariables(): List<VariableDeclaration> = myPeekTracer.getVariables()

  override fun prepareResult(): String {
    val before = myPeekTracer.beforeMap
    val after = myPeekTracer.afterMap
    return dsl.code {
      val nestedMapType = types.map(types.integerType, myCall.typeBefore)
      val mapping = linkedMap(types.integerType, types.integerType, "mapping")
      declare(mapping.defaultDeclaration())
      val eqClasses = map(myCall.typeBefore, nestedMapType, "eqClasses")
      declare(eqClasses, false)
      forEachLoop(variable(types.integerType, "beforeTime"), before.keys()) {
        val beforeValue = declare(variable(myCall.typeBefore, "beforeValue"), before.get(loopVariable), false)
        +eqClasses.computeIfAbsent(beforeValue, lambda("key") {
          +nestedMapType.defaultValue
        })
      }

      forEachLoop(variable(types.integerType, "afterTime"), after.keys()) {
        val afterTime = loopVariable
        val afterValue = declare(variable(myCall.typeAfter, "afterValue"), after.get(loopVariable), false)
        val classes = map(types.integerType, myCall.typeBefore, "classes")
        declare(classes, eqClasses.get(afterValue), false)
        forEachLoop(variable(types.integerType, "classElementTime"), classes.keys()) {
          mapping.set(loopVariable, afterTime)
        }
      }

      mapping.convertToArray(dsl, "resolve")
      myPeekTracer.prepareResult()

      declare(variable(types.anyType, "peekResult"), TextExpression(myPeekTracer.resultExpression), false)
    }
  }

  override fun getResultExpression(): String =
    dsl.newArray(dsl.types.anyType, TextExpression("peekResult"), TextExpression("resolve")).toCode()

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekTracer.additionalCallsBefore()


  override fun additionalCallsAfter(): List<IntermediateStreamCall> = myPeekTracer.additionalCallsAfter()
}