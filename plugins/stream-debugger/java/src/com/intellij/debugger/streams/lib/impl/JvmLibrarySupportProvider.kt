// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.trace.breakpoint.BreakpointBasedStreamTracer
import com.intellij.debugger.streams.trace.impl.JavaDebuggerCommandLauncher
import com.intellij.debugger.streams.trace.impl.JavaValueInterpreter
import com.intellij.debugger.streams.ui.impl.JavaCollectionTreeBuilder
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession

abstract class JvmLibrarySupportProvider : LibrarySupportProvider {
  companion object {
    private val interpreter : XValueInterpreter by lazy { JavaValueInterpreter() }
  }

  override fun getXValueInterpreter(project: Project): XValueInterpreter = interpreter

  override fun getCollectionTreeBuilder(project: Project): CollectionTreeBuilder = JavaCollectionTreeBuilder(project)

  override fun getDebuggerCommandLauncher(session: XDebugSession): DebuggerCommandLauncher {
    return JavaDebuggerCommandLauncher(session)
  }

  override suspend fun getTracerFor(chain: StreamChain, session: XDebugSession): StreamTracer {
    val support = getLibrarySupport()
    val debugProcess = session.debugProcess

    if (support !is BreakpointBasedLibrarySupport || debugProcess !is JavaDebugProcess) {
      return super.getTracerFor(chain, session)
    }

    val isSupportedVm = withDebugContext(debugProcess.debuggerSession.contextManager.context) {
      val vm = VirtualMachineProxyImpl.getCurrent()
      isSupportedVm(vm)
    }

    if (!isSupportedVm) {
      return super.getTracerFor(chain, session)
    }

    return BreakpointBasedStreamTracer(
      debugProcess,
      support,
      getXValueInterpreter(session.project),
      TraceResultInterpreterImpl(support.interpreterFactory)
    )
  }

  private fun isSupportedVm(vm: VirtualMachineProxyImpl): Boolean = vm.canForceEarlyReturn() && vm.canGetMethodReturnValues()
}
