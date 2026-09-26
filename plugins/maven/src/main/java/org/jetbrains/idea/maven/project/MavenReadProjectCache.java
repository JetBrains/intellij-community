// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MavenReadProjectCache {
  private final Map<String, MavenProjectReader.RawModelReadResult> myRawModelsCache = new HashMap<>();

  public @Nullable MavenProjectReader.RawModelReadResult get(@NotNull VirtualFile file) {
    return myRawModelsCache.get(file.getPath());
  }

  public @Nullable MavenProjectReader.RawModelReadResult get(@NotNull File file) {
    return myRawModelsCache.get(file.getAbsolutePath());
  }

  void put(@NotNull VirtualFile file, @NotNull MavenProjectReader.RawModelReadResult result) {
    myRawModelsCache.put(file.getPath(), result);
  }
}
