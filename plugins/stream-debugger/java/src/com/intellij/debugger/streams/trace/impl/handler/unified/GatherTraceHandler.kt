// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.core.trace.dsl.CodeBlock
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.dsl.Expression
import com.intellij.debugger.streams.core.trace.dsl.ListVariable
import com.intellij.debugger.streams.core.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.core.trace.impl.handler.unified.HandlerBase
import com.intellij.debugger.streams.core.trace.impl.handler.unified.PeekTraceHandler
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall

/**
 * We support gatherers only in the new breakpoint-based engine, so the dsl-based handler is effectively nop.
 * For actual implementation in the breakpoint-based engine see [com.intellij.debugger.streams.trace.breakpoint.instrumentation.GatherCallHandler]
 *
 * Since every source range is empty, `sourceTimes` is empty and `sourceOffsets` contains an initial zero plus one zero end offset per output.
 */
class GatherTraceHandler(num: Int, private val call: IntermediateStreamCall, dsl: Dsl) : HandlerBase.Intermediate(dsl) {
  private val peekTracer = PeekTraceHandler(num, "gather", call.typeBefore, call.typeAfter, dsl)
  private val sourceOffsets: ListVariable = dsl.list(dsl.types.INT, "gather${num}SourceOffsets")
  private val peekResult = dsl.variable(dsl.types.ANY, "peekResult")
  private val sourceOffsetsArray = dsl.array(dsl.types.INT, "sourceOffsetsArray")
  private val sourceTimesArray = dsl.array(dsl.types.INT, "sourceTimesArray")

  override fun additionalVariablesDeclaration(): List<VariableDeclaration> =
    peekTracer.additionalVariablesDeclaration() + sourceOffsets.defaultDeclaration()

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = peekTracer.additionalCallsBefore()

  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val recordEmptySourceRangeLambda = dsl.lambda("x") { doReturn(sourceOffsets.add(TextExpression("0"))) }
    return peekTracer.additionalCallsAfter() + dsl.createPeekCall(call.typeAfter, recordEmptySourceRangeLambda)
  }

  override fun prepareResult(): CodeBlock {
    return dsl.block {
      add(peekTracer.prepareResult())
      declare(peekResult, peekTracer.resultExpression, false)
      val offsetsSize = TextExpression("${sourceOffsets.size().toCode()} + 1")
      declare(sourceOffsetsArray, dsl.newSizedArray(dsl.types.INT, offsetsSize), false)
      declare(sourceTimesArray, dsl.newSizedArray(dsl.types.INT, 0), false)
    }
  }

  override fun getResultExpression(): Expression =
    dsl.newArray(dsl.types.ANY, peekResult, sourceOffsetsArray, sourceTimesArray)
}
