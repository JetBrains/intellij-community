// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class ToCollectionHandler(call: TerminatorStreamCall, dsl: Dsl) : TerminatorTraceHandler(call, dsl) {
  override fun getResultExpression(): Expression {
    return dsl.newArray(dsl.types.ANY, super.getResultExpression(), dsl.newArray(dsl.types.INT, dsl.currentTime()))
  }
}