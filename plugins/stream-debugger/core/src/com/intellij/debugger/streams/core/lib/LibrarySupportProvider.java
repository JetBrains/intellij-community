// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.lib;

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder;
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher;
import com.intellij.debugger.streams.core.trace.EvaluateExpressionTracer;
import com.intellij.debugger.streams.core.trace.StreamTracer;
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.core.trace.XValueInterpreter;
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface LibrarySupportProvider {
  ExtensionPointName<LibrarySupportProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.platform.debugger.streams.librarySupport");

  static @NotNull List<LibrarySupportProvider> getList() {
    return EP_NAME.getExtensionList();
  }

  @NotNull
  @NonNls String getLanguageId();

  @NotNull
  StreamChainBuilder getChainBuilder();

  @NotNull
  TraceExpressionBuilder getExpressionBuilder(@NotNull Project project);

  @NotNull
  XValueInterpreter getXValueInterpreter(@NotNull Project project);

  @NotNull
  CollectionTreeBuilder getCollectionTreeBuilder(@NotNull Project project);

  @NotNull
  LibrarySupport getLibrarySupport();

  @NotNull
  DebuggerCommandLauncher getDebuggerCommandLauncher(@NotNull XDebugSession session);

  /**
   * Returns the appropriate stream tracer for the given chain.
   * <p>
   * This method allows providers to choose between different tracing strategies
   * (e.g., expression evaluation vs. breakpoint-based) based on the chain's characteristics
   * and runtime capabilities.
   * <p>
   * Default implementation uses expression evaluation tracing.
   * JVM-specific providers can override to support breakpoint-based tracing.
   *
   * @param chain the stream chain to trace
   * @param session the debug session
   * @return a tracer suitable for this chain
   */
  @NotNull
  default StreamTracer getTracerFor(@NotNull StreamChain chain, @NotNull XDebugSession session) {
    return new EvaluateExpressionTracer(
      session,
      getExpressionBuilder(session.getProject()),
      getXValueInterpreter(session.getProject()), new TraceResultInterpreterImpl(getLibrarySupport().getInterpreterFactory())
    );
  }
}
