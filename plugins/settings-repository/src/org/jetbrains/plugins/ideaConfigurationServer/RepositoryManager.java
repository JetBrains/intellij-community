package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface RepositoryManager {
  @Nullable
  String getRemoteRepositoryUrl();

  void setRemoteRepositoryUrl(@Nullable String url);

  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  void write(@NotNull String path, @NotNull InputStream content, long size, boolean async);

  void deleteAsync(@NotNull String path);

  @NotNull
  String[] listSubFileNames(@NotNull String path);

  void updateRepository() throws IOException;

  @NotNull
  ActionCallback commit(@NotNull ProgressIndicator indicator);
}