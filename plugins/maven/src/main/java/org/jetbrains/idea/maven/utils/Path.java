// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;

public class Path {
  private final String path;

  public Path(@NotNull String path) {
    path = FileUtil.toCanonicalPath(path);
    path = FileUtil.toSystemIndependentName(path);
    this.path = path;
  }

  @NotNull
  public String getPath() {
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
    return (o instanceof Path) && path.equals(((Path)o).path);
  }
}
