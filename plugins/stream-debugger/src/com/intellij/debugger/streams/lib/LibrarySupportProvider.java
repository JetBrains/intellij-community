// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.BreakpointTracingSupport;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface LibrarySupportProvider {
  ExtensionPointName<LibrarySupportProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.debugger.streams.librarySupport");

  @NotNull
  static List<LibrarySupportProvider> getList() {
    return EP_NAME.getExtensionList();
  }

  @NotNull
  @NonNls String getLanguageId();

  @NotNull
  StreamChainBuilder getChainBuilder();

  @NotNull
  TraceExpressionBuilder getExpressionBuilder(@NotNull Project project);

  @NotNull
  LibrarySupport getLibrarySupport();

  @Nullable
  default BreakpointTracingSupport getBreakpointTracingSupport() {
    return null;
  }
}
