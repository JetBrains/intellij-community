// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.lib;

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder;
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher;
import com.intellij.debugger.streams.core.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.core.trace.XValueInterpreter;
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
}
