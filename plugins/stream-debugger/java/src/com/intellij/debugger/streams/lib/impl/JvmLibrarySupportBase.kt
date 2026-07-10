// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.impl.LibrarySupportBase
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.BreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.BreakpointResolveResult
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.IntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.NopHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.SourceCallHandler
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.TerminalCallHandler
import com.sun.jdi.ObjectReference

/**
 * Base class for JVM stream library support with breakpoint-based tracing.
 *
 * Subclasses register operations via [addIntermediateOperationsSupport]/[addTerminationOperationsSupport]
 * in their `init` blocks. Operations implementing [BreakpointBasedIntermediateOperation] or
 * [BreakpointBasedTerminalOperation] are derived from the registered operations.
 *
 * [jvmCompatibleLibrary] provides chain-of-responsibility for operations not registered here —
 * both for evaluate-expression tracing (via [LibrarySupportBase]) and for breakpoint tracing.
 */
abstract class JvmLibrarySupportBase(
  protected val jvmCompatibleLibrary: JvmLibrarySupportBase? = null,
) : LibrarySupportBase(jvmCompatibleLibrary ?: EMPTY), BreakpointBasedLibrarySupport {

  private val breakpointIntermediateOps: Map<String, BreakpointBasedIntermediateOperation> by lazy {
    supportedIntermediateOperations.values.filterIsInstance<BreakpointBasedIntermediateOperation>().associateBy { it.name }
  }
  private val breakpointTerminalOps: Map<String, BreakpointBasedTerminalOperation> by lazy {
    supportedTerminalOperations.values.filterIsInstance<BreakpointBasedTerminalOperation>().associateBy { it.name }
  }

  override val breakpointResolverFactory: BreakpointPositionResolver
    get() = EmptyBreakpointPositionResolver

  override fun canHandleChain(chain: StreamChain): Boolean {
    val intermediateOperationsSupported = chain.intermediateCalls.all { canHandleIntermediateWithBreakpoints(it.name) }
    val terminalName = chain.terminationCall.name
    val terminalOperationSupported = breakpointTerminalOps.containsKey(terminalName)
    return intermediateOperationsSupported && (terminalOperationSupported || jvmCompatibleLibrary?.canHandleChain(chain) == true)
  }

  private fun canHandleIntermediateWithBreakpoints(name: String): Boolean =
    breakpointIntermediateOps.containsKey(name)
    || jvmCompatibleLibrary?.canHandleIntermediateWithBreakpoints(name) == true

  protected open fun getSourceRuntimeHandler(
    objectStorage: ObjectStorage,
    time: ObjectReference,
  ): SourceCallHandler = jvmCompatibleLibrary?.getSourceRuntimeHandler(objectStorage, time) ?: NopHandler

  protected open fun getIntermediateRuntimeHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler? {
    return breakpointIntermediateOps[call.name]?.getRuntimeTraceHandler(objectStorage, callOrder, call, time)
           ?: jvmCompatibleLibrary?.getIntermediateRuntimeHandler(objectStorage, callOrder, call, time)
  }

  protected open fun getTerminalRuntimeHandler(
    call: TerminatorStreamCall,
    objectStorage: ObjectStorage,
    time: ObjectReference,
  ): TerminalCallHandler? {
    return breakpointTerminalOps[call.name]?.getRuntimeTraceHandler(objectStorage, call, time)
           ?: jvmCompatibleLibrary?.getTerminalRuntimeHandler(call, objectStorage, time)
  }

  override fun createRuntimeHandlerFactory(objectStorage: ObjectStorage): BreakpointBasedHandlerFactory {
    return CounterBasedBreakpointBasedHandlerFactory(
      objectStorage,
      getSourceHandler = { time -> getSourceRuntimeHandler(objectStorage, time) },
      getIntermediateHandler = { callOrder, call, time ->
        getIntermediateRuntimeHandler(objectStorage, callOrder, call, time)
        ?: error("No breakpoint handler registered for terminal operation '${call.name}'")
      },
      getTerminalHandler = { call, time ->
        getTerminalRuntimeHandler(call, objectStorage, time)
        ?: error("No breakpoint handler registered for terminal operation '${call.name}'")
      },
    )
  }
}

private object EmptyBreakpointPositionResolver : BreakpointPositionResolver {
  override suspend fun findBreakpointPositions(chain: StreamChain): BreakpointResolveResult = BreakpointResolveResult.NotFound
}
