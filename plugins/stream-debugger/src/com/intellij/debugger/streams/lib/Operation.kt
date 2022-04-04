// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib

import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.TerminatorCallHandler
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.openapi.util.NlsSafe

/**
 * @author Vitaliy.Bibaev
 */
interface Operation {
  @get:NlsSafe
  val name: String

  val traceInterpreter: CallTraceInterpreter

  val valuesOrderResolver: ValuesOrderResolver
}

interface IntermediateOperation : Operation {
  fun getTraceHandler(callOrder: Int, call: IntermediateStreamCall, dsl: Dsl): IntermediateCallHandler

  fun getRuntimeTraceHandler(callOrder: Int, call: IntermediateStreamCall, valueManager: ValueManager): RuntimeIntermediateCallHandler? {
    return null
  }
}

interface TerminalOperation : Operation {
  fun getTraceHandler(call: TerminatorStreamCall, resultExpression: String, dsl: Dsl): TerminatorCallHandler

  fun getRuntimeTraceHandler(call: TerminatorStreamCall, valueManager: ValueManager): RuntimeTerminalCallHandler? {
    return null
  }
}