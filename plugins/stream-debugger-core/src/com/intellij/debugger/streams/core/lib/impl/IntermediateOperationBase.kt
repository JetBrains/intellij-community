// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib.impl

import com.intellij.debugger.streams.core.lib.IntermediateOperation
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.IntermediateCallHandler
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.openapi.util.NlsSafe

/**
 * @author Vitaliy.Bibaev
 */
abstract class IntermediateOperationBase(override val name: @NlsSafe String,
                                         private val handlerFactory: (Int, IntermediateStreamCall, Dsl) -> IntermediateCallHandler,
                                         override val traceInterpreter: CallTraceInterpreter,
                                         override val valuesOrderResolver: ValuesOrderResolver) : IntermediateOperation {
  override fun getTraceHandler(callOrder: Int, call: IntermediateStreamCall, dsl: Dsl): IntermediateCallHandler =
    handlerFactory.invoke(callOrder, call, dsl)
}