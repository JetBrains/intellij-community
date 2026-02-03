// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.util.List;

public class WCInfoWithBranches extends WCInfo {

  private final @NotNull List<Branch> myBranches;
  private final @NotNull VirtualFile myRoot;
  private final Branch myCurrentBranch;

  public WCInfoWithBranches(@NotNull WCInfo info, @NotNull List<Branch> branches, @NotNull VirtualFile root, Branch currentBranch) {
    super(info.getRootInfo(), info.isIsWcRoot(), info.getStickyDepth());

    myBranches = branches;
    myRoot = root;
    myCurrentBranch = currentBranch;
  }

  // to be used in combo
  @Override
  public String toString() {
    return getPath();
  }

  @Override
  public @NotNull VirtualFile getVcsRoot() {
    return myRoot;
  }

  /**
   * List of all branches according to branch configuration. Does not contain {@code getCurrentBranch()} branch.
   */
  public @NotNull List<Branch> getBranches() {
    return myBranches;
  }

  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  /**
   * Current branch of this working copy instance (working copy url) according to branch configuration.
   */
  public Branch getCurrentBranch() {
    return myCurrentBranch;
  }

  public static class Branch {
    private final @NotNull Url myUrl;

    public Branch(@NotNull Url url) {
      myUrl = url;
    }

    public @NotNull String getName() {
      return myUrl.getTail();
    }

    public @NotNull Url getUrl() {
      return myUrl;
    }

    @Override
    public String toString() {
      return getName();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Branch branch = (Branch)o;

      return myUrl.equals(branch.myUrl);
    }

    @Override
    public int hashCode() {
      return myUrl.hashCode();
    }
  }
}
