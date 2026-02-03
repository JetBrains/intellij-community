// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib

import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.IntermediateCallHandler
import com.intellij.debugger.streams.core.trace.TerminatorCallHandler
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
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
}

interface TerminalOperation : Operation {
  fun getTraceHandler(call: TerminatorStreamCall, resultExpression: String, dsl: Dsl): TerminatorCallHandler
}