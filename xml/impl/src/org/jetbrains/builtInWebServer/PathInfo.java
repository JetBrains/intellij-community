package org.jetbrains.builtInWebServer;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathInfo {
  private final VirtualFile child;
  private final VirtualFile root;
  String moduleName;

  private String computedPath;

  public PathInfo(@NotNull VirtualFile child, @NotNull VirtualFile root, @Nullable String moduleName) {
    this.child = child;
    this.root = root;
    this.moduleName = moduleName;
  }

  public PathInfo(@NotNull VirtualFile child, @NotNull VirtualFile root) {
    this(child, root, null);
  }

  @NotNull
  public VirtualFile getChild() {
    return child;
  }

  @NotNull
  public VirtualFile getRoot() {
    return root;
  }

  @Nullable
  public String getModuleName() {
    return moduleName;
  }

  @NotNull
  public String getPath() {
    if (computedPath == null) {
      computedPath = (moduleName == null ? "" : moduleName + '/') + VfsUtilCore.getRelativePath(child, root, '/');
    }
    return computedPath;
  }
}