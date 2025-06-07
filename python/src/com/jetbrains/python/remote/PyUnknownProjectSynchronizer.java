// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.Result;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sync project for SDKs that does not allow sync. Always denies to sync anything.
 * Singleton, so use instance
 */
@ApiStatus.Internal
public final class PyUnknownProjectSynchronizer implements PyProjectSynchronizer {
  public static final PyProjectSynchronizer INSTANCE = new PyUnknownProjectSynchronizer();

  private PyUnknownProjectSynchronizer() {
  }

  @Override
  public @DialogMessage @NotNull String checkSynchronizationAvailable(final @NotNull PySyncCheckStrategy syncCheckStrategy) {
    return PyBundle.message("python.unknown.project.synchronizer.this.interpreter.type.does.not.support.remote.project.creation");
  }

  @Override
  public @Nullable String mapFilePath(final @NotNull Project project, final @NotNull PySyncDirection direction, final @NotNull String filePath) {
    return null;
  }

  @Override
  public @Nullable String getDefaultRemotePath() {
    return null;
  }

  @Override
  public @Nullable Result<List<PathMappingSettings.PathMapping>, String> getAutoMappings() {
    return null;
  }

  @Override
  public void syncProject(final @NotNull Module module,
                          final @NotNull PySyncDirection syncDirection,
                          final @Nullable Consumer<Boolean> callback,
                          final String @NotNull ... fileNames) {
    if (callback != null) {
      callback.accept(false);
    }
  }
}
