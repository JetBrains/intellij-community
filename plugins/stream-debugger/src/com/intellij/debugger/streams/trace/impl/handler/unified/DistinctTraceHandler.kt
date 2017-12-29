// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class DistinctTraceHandler(num: Int, private val myCall: IntermediateStreamCall, dsl: Dsl) : HandlerBase.Intermediate(dsl) {
  private val myPeekTracer = PeekTraceHandler(num, "distinct", myCall.typeBefore, myCall.typeAfter, dsl)
  override fun additionalVariablesDeclaration(): List<VariableDeclaration> =
    myPeekTracer.additionalVariablesDeclaration()

  override fun prepareResult(): CodeBlock {
    val before = myPeekTracer.beforeMap
    val after = myPeekTracer.afterMap
    return dsl.block {
      val nestedMapType = types.map(types.INT, myCall.typeBefore)
      val mapping = linkedMap(types.INT, types.INT, "mapping")
      declare(mapping.defaultDeclaration())
      val eqClasses = map(myCall.typeBefore, nestedMapType, "eqClasses")
      declare(eqClasses, TextExpression(eqClasses.type.defaultValue), false)
      forEachLoop(variable(types.INT, "beforeTime"), before.keys()) {
        val beforeValue = declare(variable(myCall.typeBefore, "beforeValue"), before.get(loopVariable), false)
        val computeIfAbsentExpression = eqClasses.computeIfAbsent(beforeValue, lambda("key") {
          doReturn(TextExpression(nestedMapType.defaultValue))
        })
        val classItems = map(types.INT, myCall.typeBefore, "classItems")
        declare(classItems, computeIfAbsentExpression, false)
        statement { classItems.set(loopVariable, beforeValue) }
      }

      forEachLoop(variable(types.INT, "afterTime"), after.keys()) {
        val afterTime = loopVariable
        val afterValue = declare(variable(myCall.typeAfter, "afterValue"), after.get(loopVariable), false)
        val classes = map(types.INT, myCall.typeBefore, "classes")
        declare(classes, eqClasses.get(afterValue), false)
        forEachLoop(variable(types.INT, "classElementTime"), classes.keys()) {
          statement { mapping.set(loopVariable, afterTime) }
        }
      }

      add(mapping.convertToArray(dsl, "resolve"))
      add(myPeekTracer.prepareResult())

      declare(variable(types.ANY, "peekResult"), myPeekTracer.resultExpression, false)
    }
  }

  override fun getResultExpression(): Expression =
    dsl.newArray(dsl.types.ANY, TextExpression("peekResult"), TextExpression("resolve"))

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekTracer.additionalCallsBefore()


  override fun additionalCallsAfter(): List<IntermediateStreamCall> = myPeekTracer.additionalCallsAfter()
}