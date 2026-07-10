// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.lib

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.EvaluateExpressionTracer
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.NonNls

/**
 * @author Vitaliy.Bibaev
 */
interface LibrarySupportProvider {
  fun getLanguageId(): @NonNls String

  fun getChainBuilder(): StreamChainBuilder

  fun getExpressionBuilder(project: Project): TraceExpressionBuilder

  fun getXValueInterpreter(project: Project): XValueInterpreter

  fun getCollectionTreeBuilder(project: Project): CollectionTreeBuilder

  fun getLibrarySupport(): LibrarySupport

  fun getDebuggerCommandLauncher(session: XDebugSession): DebuggerCommandLauncher

  /**
   * Returns the appropriate stream tracer for the given chain.
   *
   *
   * This method allows providers to choose between different tracing strategies
   * (e.g., expression evaluation vs. breakpoint-based) based on the chain's characteristics
   * and runtime capabilities.
   *
   *
   * Default implementation uses expression evaluation tracing.
   * JVM-specific providers can override to support breakpoint-based tracing.
   *
   * @param chain the stream chain to trace
   * @param session the debug session
   * @return a tracer suitable for this chain
   */
  suspend fun getTracerFor(chain: StreamChain, session: XDebugSession): StreamTracer {
    val project = session.getProject()
    return EvaluateExpressionTracer(
      session,
      getExpressionBuilder(project),
      getXValueInterpreter(project),
      TraceResultInterpreterImpl(getLibrarySupport().interpreterFactory),
      getDebuggerCommandLauncher(session)
    )
  }

  companion object {
    val EP_NAME: ExtensionPointName<LibrarySupportProvider> = create("org.jetbrains.platform.debugger.streams.librarySupport")
  }
}
