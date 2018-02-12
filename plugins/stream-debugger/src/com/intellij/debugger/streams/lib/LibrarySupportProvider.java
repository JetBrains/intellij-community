// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib;

import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface LibrarySupportProvider {
  ExtensionPointName<LibrarySupportProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.debugger.streams.librarySupport");

  @NotNull
  static List<LibrarySupportProvider> getList() {
    final LibrarySupportProvider[] extensions = Extensions.getExtensions(EP_NAME);
    return Arrays.asList(extensions);
  }

  @NotNull
  String getLanguageId();

  @NotNull
  StreamChainBuilder getChainBuilder();

  @NotNull
  TraceExpressionBuilder getExpressionBuilder(@NotNull Project project);

  @NotNull
  LibrarySupport getLibrarySupport();
}
