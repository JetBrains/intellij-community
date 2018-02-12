// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;
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
  @NotNull private final Depth myStickyDepth;
  @NotNull private final RootUrlInfo myRootInfo;

  public WCInfo(@NotNull RootUrlInfo rootInfo, boolean isWcRoot, @NotNull Depth stickyDepth) {
    myRootInfo = rootInfo;
    myIsWcRoot = isWcRoot;
    myStickyDepth = stickyDepth;
  }

  @NotNull
  public Depth getStickyDepth() {
    return myStickyDepth;
  }

  @NotNull
  public String getPath() {
    return myRootInfo.getPath();
  }

  @Nullable
  public VirtualFile getVcsRoot() {
    return null;
  }

  @NotNull
  public Url getUrl() {
    return myRootInfo.getUrl();
  }

  @NotNull
  public Url getRepoUrl() {
    return myRootInfo.getRepositoryUrl();
  }

  @NotNull
  public RootUrlInfo getRootInfo() {
    return myRootInfo;
  }

  public boolean hasError() {
    return getRootInfo().getNode().hasError();
  }

  @NotNull
  public String getErrorMessage() {
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    SvnBindException error = getRootInfo().getNode().getError();

    return error != null ? error.getMessage() : "";
  }

  @NotNull
  public WorkingCopyFormat getFormat() {
    return myRootInfo.getFormat();
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo)) return false;

    final WCInfo wcInfo = (WCInfo)o;

    return getPath().equals(wcInfo.getPath());
  }

  @Override
  public int hashCode() {
    return getPath().hashCode();
  }

  @Nullable
  public NestedCopyType getType() {
    return myRootInfo.getType();
  }
}
