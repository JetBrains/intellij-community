package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public interface RepositoryManager {
  @Nullable
  String getRemoteRepositoryUrl();

  void setRemoteRepositoryUrl(@Nullable String url);

  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  void write(@NotNull String path, @NotNull byte[] content, int size, boolean async);

  void deleteAsync(@NotNull String path);

  @NotNull
  Collection<String> listSubFileNames(@NotNull String path);

  void updateRepository();

  @NotNull
  ActionCallback commit();

  @NotNull
  ActionCallback push(@NotNull ProgressIndicator indicator);

  @NotNull
  ActionCallback pull(@NotNull ProgressIndicator indicator);
}