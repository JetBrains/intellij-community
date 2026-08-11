// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.BreakpointBasedLibrarySupport
import com.intellij.debugger.streams.trace.breakpoint.BreakpointBasedStreamTracer
import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.XDebugSession

internal open class BreakpointBasedExecutionTestCaseHelper(
  testCase: ExecutionTestCase,
  session: XDebugSession,
  librarySupportProvider: LibrarySupportProvider,
  debuggerPositionResolver: DebuggerPositionResolver,
  logger: Logger,
) : ExecutionTestCaseHelper(testCase, session, librarySupportProvider, debuggerPositionResolver, logger) {

  override fun createTracer(chain: StreamChain, commandLauncher: DebuggerCommandLauncher): StreamTracer {
    val javaDebugProcess = session.debugProcess as? JavaDebugProcess
                           ?: error("Expected JavaDebugProcess")
    val librarySupport = librarySupportProvider.getLibrarySupport() as? BreakpointBasedLibrarySupport
                         ?: error("Library does not support breakpoint-based tracing")
    return BreakpointBasedStreamTracer(
      javaDebugProcess,
      librarySupport,
      createXValueInterpreter(),
      createResultInterpreter(),
    )
  }
}
