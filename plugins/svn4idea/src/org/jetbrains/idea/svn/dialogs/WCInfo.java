// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public class WCInfo {

  private final boolean myIsWcRoot;
  private final @NotNull Depth myStickyDepth;
  private final @NotNull RootUrlInfo myRootInfo;

  public WCInfo(@NotNull RootUrlInfo rootInfo, boolean isWcRoot, @NotNull Depth stickyDepth) {
    myRootInfo = rootInfo;
    myIsWcRoot = isWcRoot;
    myStickyDepth = stickyDepth;
  }

  public @NotNull Depth getStickyDepth() {
    return myStickyDepth;
  }

  public @NlsSafe @NotNull String getPath() {
    return myRootInfo.getPath();
  }

  public @Nullable VirtualFile getVcsRoot() {
    return null;
  }

  public @NotNull Url getUrl() {
    return myRootInfo.getUrl();
  }

  public @NotNull Url getRepoUrl() {
    return myRootInfo.getRepositoryUrl();
  }

  public @NotNull RootUrlInfo getRootInfo() {
    return myRootInfo;
  }

  public boolean hasError() {
    return getRootInfo().getNode().hasError();
  }

  public @Nls @NotNull String getErrorMessage() {
    SvnBindException error = getRootInfo().getNode().getError();

    return error != null ? error.getMessage() : "";
  }

  public @NotNull WorkingCopyFormat getFormat() {
    return myRootInfo.getFormat();
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo wcInfo)) return false;

    return getPath().equals(wcInfo.getPath());
  }

  @Override
  public int hashCode() {
    return getPath().hashCode();
  }

  public @Nullable NestedCopyType getType() {
    return myRootInfo.getType();
  }
}
