// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.filtering.filterTraceableStreams
import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.java.rt.StreamDebuggerUtils
import com.intellij.debugger.streams.trace.breakpoint.BreakpointBasedStreamTracer
import com.intellij.debugger.streams.trace.impl.JavaDebuggerCommandLauncher
import com.intellij.debugger.streams.trace.impl.JavaValueInterpreter
import com.intellij.debugger.streams.ui.impl.JavaCollectionTreeBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebugSession

abstract class JvmLibrarySupportProvider : LibrarySupportProvider {
  override fun getXValueInterpreter(project: Project): XValueInterpreter = interpreter

  override fun getCollectionTreeBuilder(project: Project): CollectionTreeBuilder = JavaCollectionTreeBuilder(project)

  override fun getDebuggerCommandLauncher(session: XDebugSession): DebuggerCommandLauncher {
    return JavaDebuggerCommandLauncher(session)
  }

  override suspend fun getTracerFor(chain: StreamChain, session: XDebugSession): StreamTracer {
    if (!Registry.`is`("debugger.streams.use.breakpoint.based.tracing")) {
      return super.getTracerFor(chain, session)
    }
    val support = getLibrarySupport()
    val debugProcess = session.debugProcess

    if (support !is BreakpointBasedLibrarySupport || debugProcess !is JavaDebugProcess) {
      return super.getTracerFor(chain, session)
    }

    if (!support.canHandleChain(chain)) {
      return super.getTracerFor(chain, session)
    }

    val debuggerContext = debugProcess.debuggerSession.contextManager.context
    val canUseBreakpointEngine = withDebugContext(debuggerContext) {
      val vm = VirtualMachineProxyImpl.getCurrent()
      isSupportedVm(vm) && canLoadRuntimeLibrary(debuggerContext)
    }

    if (!canUseBreakpointEngine) {
      return super.getTracerFor(chain, session)
    }

    return BreakpointBasedStreamTracer(
      debugProcess,
      support,
      getXValueInterpreter(session.project),
      TraceResultInterpreterImpl(support.interpreterFactory)
    )
  }

  override suspend fun filterTraceableStreams(session: XDebugSession, chains: List<StreamChain>): List<StreamChain> {
    if (chains.isEmpty()) return chains
    val debugProcess = session.debugProcess as? JavaDebugProcess ?: return chains
    val position = session.currentPosition ?: return chains
    val context = debugProcess.debuggerSession.contextManager.context
    return withDebugContext(context) {
      val location = runCatching { context.frameProxy?.location() }.getOrNull() ?: return@withDebugContext chains
      filterTraceableStreams(session.project, chains, position, location.method(), location.codeIndex())
    }
  }

  private fun isSupportedVm(vm: VirtualMachineProxyImpl): Boolean = vm.canForceEarlyReturn() && vm.canGetMethodReturnValues()

  /**
   * Fail-fast probe that must run on the debugger manager thread.
   * We define one representative helper class up front (the load is cached per target VM, so the tracer reuses it) and,
   * on failure, fall back to the expression-based engine before committing to the breakpoint-based one.
   */
  private fun canLoadRuntimeLibrary(debuggerContext: DebuggerContextImpl): Boolean {
    val evaluationContext = debuggerContext.createEvaluationContext() ?: let {
      LOG.warn("Cannot check stream debugger runtime support because the program is not suspended, falling back to the expression-based engine")
      return false
    }
    return try {
      val loaded = ClassLoadingUtils.getHelperClass(StreamDebuggerUtils::class.java, evaluationContext) != null
      if (!loaded) {
        LOG.warn("Stream debugger runtime support is unavailable in the debuggee, falling back to the expression-based engine")
      }
      loaded
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to load stream debugger runtime support into the debuggee, falling back to the expression-based engine", e)
      false
    }
  }

  companion object {
    private val LOG = logger<JvmLibrarySupportProvider>()
    private val interpreter : XValueInterpreter by lazy { JavaValueInterpreter() }
  }
}
