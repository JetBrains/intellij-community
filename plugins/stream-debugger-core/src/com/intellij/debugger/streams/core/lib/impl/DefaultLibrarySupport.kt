// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib.impl

import com.intellij.debugger.streams.core.lib.HandlerFactory
import com.intellij.debugger.streams.core.lib.InterpreterFactory
import com.intellij.debugger.streams.core.lib.LibrarySupport
import com.intellij.debugger.streams.core.lib.ResolverFactory
import com.intellij.debugger.streams.core.resolve.EmptyResolver
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.IntermediateCallHandler
import com.intellij.debugger.streams.core.trace.TerminatorCallHandler
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.impl.handler.unified.PeekTraceHandler
import com.intellij.debugger.streams.core.trace.impl.handler.unified.TerminatorTraceHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.SimplePeekCallTraceInterpreter
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamCallType
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall

class DefaultLibrarySupport : LibrarySupport {
  override fun createHandlerFactory(dsl: Dsl): HandlerFactory = object : HandlerFactory {
    override fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler {
      return PeekTraceHandler(number, call.name, call.typeBefore, call.typeAfter, dsl)
    }

    override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TerminatorCallHandler {
      return TerminatorTraceHandler(call, dsl)
    }
  }

  override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
    override fun getInterpreter(callName: String, callType: StreamCallType): CallTraceInterpreter {
      return SimplePeekCallTraceInterpreter()
    }
  }

  override val resolverFactory: ResolverFactory = object : ResolverFactory {
    override fun getResolver(callName: String, callType: StreamCallType): ValuesOrderResolver {
      return EmptyResolver()
    }
  }
}