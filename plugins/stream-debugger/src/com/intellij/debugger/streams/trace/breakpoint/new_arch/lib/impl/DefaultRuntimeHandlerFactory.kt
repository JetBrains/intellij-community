// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl

import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeSourceCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.handlers.NopCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.handlers.PeekCallHandler
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.impl.handlers.PeekTerminalCallHandler
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

class DefaultRuntimeHandlerFactory(private val valueManager: ValueManager) : RuntimeHandlerFactory {
  override fun getForSource(): RuntimeSourceCallHandler = NopCallHandler()

  override fun getForIntermediate(call: IntermediateStreamCall): RuntimeIntermediateCallHandler {
    return PeekCallHandler(valueManager, call.typeBefore, call.typeAfter)
  }

  override fun getForTermination(call: TerminatorStreamCall): RuntimeTerminalCallHandler {
    return PeekTerminalCallHandler(valueManager, call.typeBefore, call.resultType)
  }
}