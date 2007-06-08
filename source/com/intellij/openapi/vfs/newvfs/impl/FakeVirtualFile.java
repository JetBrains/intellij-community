/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class FakeVirtualFile extends StubVirtualFile {
  private final VirtualFile myParent;
  private final String myName;

  public FakeVirtualFile(@NotNull final String name, @NotNull final VirtualFile parent) {
    myName = name;
    myParent = parent;
  }

  @Nullable
  public VirtualFile getParent() {
    return myParent;
  }

  public String getPath() {
    final String basePath = myParent.getPath();
    if (basePath.endsWith("/")) return basePath + myName;
    return basePath + '/' + myName;
  }

  @NotNull
  @NonNls
  public String getName() {
    return myName;
  }
}