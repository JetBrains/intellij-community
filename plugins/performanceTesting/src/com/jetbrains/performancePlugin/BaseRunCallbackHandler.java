package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;

final class BaseRunCallbackHandler implements RunCallbackHandler {
  @Override
  public void patchCommandCallback(Project project, CompletableFuture<?> callback) { }
}
