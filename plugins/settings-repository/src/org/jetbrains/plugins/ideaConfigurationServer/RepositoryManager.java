package org.jetbrains.plugins.ideaConfigurationServer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface RepositoryManager {
  @Nullable
  InputStream read(@NotNull String path) throws IOException;

  void write(@NotNull String path, @NotNull InputStream content, long size, boolean async);

  void deleteAsync(@NotNull String path);

  @NotNull
  String[] listSubFileNames(@NotNull String path);

  void updateRepository() throws IOException;

  void commit();
}