// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;

final class BaseRunCallbackHandler implements RunCallbackHandler {
  @Override
  public void patchCommandCallback(Project project, CompletableFuture<?> callback) { }
}
