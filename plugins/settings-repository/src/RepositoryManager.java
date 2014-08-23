package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.progress.ProgressIndicator;
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

  boolean hasUpstream();

  /**
   * Return error message if failed
   */
  void setUpstream(@Nullable String url, @Nullable String branch) throws Exception;

  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  /**
   * @param async Write postpone or immediately
   */
  void write(@NotNull String path, @NotNull byte[] content, int size, boolean async);

  void delete(@NotNull String path);

  @NotNull
  Collection<String> listSubFileNames(@NotNull String path);

  void updateRepository(@NotNull ProgressIndicator indicator) throws Exception;

  /**
   * Not all implementations support progress indicator (will not be updated on progress)
   */
  void commit(@NotNull ProgressIndicator indicator) throws Exception;

  void commit(@NotNull List<String> paths);

  void push(@NotNull ProgressIndicator indicator) throws Exception;

  void pull(@NotNull ProgressIndicator indicator) throws Exception;

  void initRepository(@NotNull File dir) throws IOException;

  boolean has(@NotNull String path);

  boolean isValidRepository(@NotNull File file);
}
