package org.jetbrains.plugins.ideaConfigurationServer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface RepositoryManager {
  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  void write(@NotNull String path, @NotNull InputStream content, long size) throws IOException;

  void delete(@NotNull String path) throws IOException;

  @NotNull
  String[] listSubFileNames(@NotNull String path);

  void updateRepo() throws IOException;
}