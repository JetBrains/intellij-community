package org.jetbrains.idea.maven.core.util;

import com.intellij.util.PathUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;

public class Path {
  private String path;

  public Path(String path) {
    path = PathUtil.getCanonicalPath(path);
    path = FileUtil.toSystemIndependentName(path);
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  public Url toUrl() {
    return new Url(VfsUtil.pathToUrl(path));
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
