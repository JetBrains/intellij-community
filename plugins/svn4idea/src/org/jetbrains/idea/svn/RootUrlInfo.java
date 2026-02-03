// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;

public final class RootUrlInfo implements RootUrlPair {

  private final @NotNull WorkingCopyFormat myFormat;
  private final @NotNull Node myNode;
  // vcs root
  private final @NotNull VirtualFile myRoot;
  private volatile @Nullable NestedCopyType myType;

  public RootUrlInfo(final @NotNull Node node, final @NotNull WorkingCopyFormat format, final @NotNull VirtualFile root) {
    this(node, format, root, null);
  }

  public RootUrlInfo(final @NotNull Node node,
                     final @NotNull WorkingCopyFormat format,
                     final @NotNull VirtualFile root,
                     final @Nullable NestedCopyType type) {
    myNode = node;
    myFormat = format;
    myRoot = root;
    myType = type;
  }

  public @NotNull Node getNode() {
    return myNode;
  }

  public @NotNull Url getRepositoryUrl() {
    return myNode.getRepositoryRootUrl();
  }

  public @NotNull WorkingCopyFormat getFormat() {
    return myFormat;
  }

  public @NotNull File getIoFile() {
    return myNode.getIoFile();
  }

  public @NlsSafe @NotNull String getPath() {
    return getIoFile().getAbsolutePath();
  }

  // vcs root
  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return myNode.getFile();
  }

  @Override
  public @NotNull Url getUrl() {
    return myNode.getUrl();
  }

  public @Nullable NestedCopyType getType() {
    return myType;
  }

  public void setType(@Nullable NestedCopyType type) {
    myType = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RootUrlInfo info = (RootUrlInfo)o;

    if (myFormat != info.myFormat) return false;
    if (!myNode.equals(info.myNode)) return false;
    if (!myRoot.equals(info.myRoot)) return false;
    if (myType != info.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFormat.hashCode();
    result = 31 * result + myNode.hashCode();
    result = 31 * result + myRoot.hashCode();
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
