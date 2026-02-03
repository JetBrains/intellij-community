// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;

public class MavenPathWrapper {
  private final String path;

  public MavenPathWrapper(@NotNull String path) {
    path = FileUtil.toCanonicalPath(path);
    path = FileUtil.toSystemIndependentName(path);
    this.path = path;
  }

  public @NotNull String getPath() {
    return path;
  }

  public Url toUrl() {
    return new Url(VfsUtilCore.pathToUrl(path));
  }

  @Override
  public String toString() {
    return path;
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    return (o instanceof MavenPathWrapper) && path.equals(((MavenPathWrapper)o).path);
  }
}
