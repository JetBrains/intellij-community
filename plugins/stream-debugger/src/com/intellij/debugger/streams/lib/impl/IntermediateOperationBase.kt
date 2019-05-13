// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.IntermediateOperation
import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
abstract class IntermediateOperationBase(override val name: String,
                                         private val handlerFactory: (Int, IntermediateStreamCall, Dsl) -> IntermediateCallHandler,
                                         override val traceInterpreter: CallTraceInterpreter,
                                         override val valuesOrderResolver: ValuesOrderResolver) : IntermediateOperation {
  override fun getTraceHandler(callOrder: Int, call: IntermediateStreamCall, dsl: Dsl): IntermediateCallHandler =
    handlerFactory.invoke(callOrder, call, dsl)
}