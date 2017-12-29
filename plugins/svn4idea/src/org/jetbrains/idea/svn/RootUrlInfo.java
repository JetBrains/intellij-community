// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;

public class RootUrlInfo implements RootUrlPair {

  @NotNull private final WorkingCopyFormat myFormat;
  @NotNull private final Node myNode;
  // vcs root
  @NotNull private final VirtualFile myRoot;
  @Nullable private volatile NestedCopyType myType;

  public RootUrlInfo(@NotNull final Node node, @NotNull final WorkingCopyFormat format, @NotNull final VirtualFile root) {
    this(node, format, root, null);
  }

  public RootUrlInfo(@NotNull final Node node,
                     @NotNull final WorkingCopyFormat format,
                     @NotNull final VirtualFile root,
                     @Nullable final NestedCopyType type) {
    myNode = node;
    myFormat = format;
    myRoot = root;
    myType = type;
  }

  @NotNull
  public Node getNode() {
    return myNode;
  }

  @NotNull
  public Url getRepositoryUrl() {
    return myNode.getRepositoryRootUrl();
  }

  @NotNull
  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  @NotNull
  public File getIoFile() {
    return myNode.getIoFile();
  }

  @NotNull
  public String getPath() {
    return getIoFile().getAbsolutePath();
  }

  // vcs root
  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myNode.getFile();
  }

  @NotNull
  public Url getUrl() {
    return myNode.getUrl();
  }

  @Nullable
  public NestedCopyType getType() {
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
