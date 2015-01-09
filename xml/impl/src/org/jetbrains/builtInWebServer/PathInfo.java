package org.jetbrains.builtInWebServer;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathInfo {
  private final VirtualFile child;
  private final VirtualFile root;
  private final boolean isLibrary;

  String moduleName;

  private String computedPath;

  public PathInfo(@NotNull VirtualFile child, @NotNull VirtualFile root, @Nullable String moduleName, boolean isLibrary) {
    this.child = child;
    this.root = root;
    this.moduleName = moduleName;
    this.isLibrary = isLibrary;
  }

  public PathInfo(@NotNull VirtualFile child, @NotNull VirtualFile root) {
    this(child, root, null, false);
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
      StringBuilder builder = new StringBuilder();
      if (moduleName != null) {
        builder.append(moduleName).append('/');
      }

      if (isLibrary) {
        builder.append(root.getName()).append('/');
      }

      computedPath = builder.append(VfsUtilCore.getRelativePath(child, root, '/')).toString();
    }
    return computedPath;
  }
}