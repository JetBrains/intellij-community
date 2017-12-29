// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.openapi.util.TextRange

/**
 * @author Vitaliy.Bibaev
 */
class ParallelHandler(num: Int, private val call: IntermediateStreamCall, dsl: Dsl)
  : PeekTraceHandler(num, call.name, call.typeBefore, call.typeAfter, dsl) {
  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val calls = ArrayList(super.additionalCallsAfter())
    calls.add(0, SequentialCall(call.typeBefore))
    return calls
  }

  private class SequentialCall(elementsType: GenericType)
    : IntermediateStreamCallImpl("sequential", emptyList(), elementsType, elementsType, TextRange.EMPTY_RANGE)
}