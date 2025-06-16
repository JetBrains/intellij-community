// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;

public interface RunCallbackHandler {
  ExtensionPointName<RunCallbackHandler> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.runCallbackHandler");

  void patchCommandCallback(Project project, CompletableFuture<?> callback);

  static void applyPatchesToCommandCallback(Project project, CompletableFuture<?> callback) {
    EP_NAME.getExtensionList().forEach(extension ->
      extension.patchCommandCallback(project, callback)
    );
  }
}
