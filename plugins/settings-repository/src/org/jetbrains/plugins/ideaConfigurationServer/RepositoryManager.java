package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public interface RepositoryManager {
  @Nullable
  String getRemoteRepositoryUrl();

  @Nullable
  /**
   * Return error message if failed
   */
  void setRemoteRepositoryUrl(@Nullable String url) throws Exception;

  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  /**
   * @param async Write postpone or immediately
   */
  void write(@NotNull String path, @NotNull byte[] content, int size, boolean async);

  void deleteAsync(@NotNull String path);

  @NotNull
  Collection<String> listSubFileNames(@NotNull String path);

  void updateRepository();

  @NotNull
  /**
   * Not all implementations support progress indicator (will not be updated on progress)
   */
  ActionCallback commit(@NotNull ProgressIndicator indicator);

  void commit(@NotNull List<String> paths);

  @NotNull
  ActionCallback push(@NotNull ProgressIndicator indicator);

  @NotNull
  ActionCallback pull(@NotNull ProgressIndicator indicator);

  void initRepository(@NotNull File dir) throws IOException;

  boolean has(String path);

  boolean isValidRepository(@NotNull File file);
}