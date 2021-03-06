package com.jetbrains.python.remote;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.Result;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sync project for SDKs that does not allow sync. Always denies to sync anything.
 * Singleton, so use instance
 *
 * @author Ilya.Kazakevich
 */
final class PyUnknownProjectSynchronizer implements PyProjectSynchronizer {

  static final PyProjectSynchronizer INSTANCE = new PyUnknownProjectSynchronizer();

  private PyUnknownProjectSynchronizer() {
  }

  @DialogMessage
  @Override
  @Nullable
  public String checkSynchronizationAvailable(@NotNull final PySyncCheckStrategy syncCheckStrategy) {
    return PyBundle.message("python.unknown.project.synchronizer.this.interpreter.type.does.not.support.remote.project.creation");
  }

  @Nullable
  @Override
  public String mapFilePath(@NotNull final Project project, @NotNull final PySyncDirection direction, @NotNull final String filePath) {
    return null;
  }

  @Override
  @Nullable
  public String getDefaultRemotePath() {
    return null;
  }

  @Nullable
  @Override
  public Result<List<PathMappingSettings.PathMapping>, String> getAutoMappings() {
    return null;
  }

  @Override
  public void syncProject(@NotNull final Module module,
                          @NotNull final PySyncDirection syncDirection,
                          @Nullable final Consumer<Boolean> callback,
                          final String @NotNull ... fileNames) {
    if (callback != null) {
      callback.accept(false);
    }
  }
}
