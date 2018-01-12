// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.util.List;

public class WCInfoWithBranches extends WCInfo {

  @NotNull private final List<Branch> myBranches;
  @NotNull private final VirtualFile myRoot;
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
  @NotNull
  public VirtualFile getVcsRoot() {
    return myRoot;
  }

  /**
   * List of all branches according to branch configuration. Does not contain {@code getCurrentBranch()} branch.
   */
  @NotNull
  public List<Branch> getBranches() {
    return myBranches;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  /**
   * Current branch of this working copy instance (working copy url) according to branch configuration.
   */
  public Branch getCurrentBranch() {
    return myCurrentBranch;
  }

  public static class Branch {

    @NotNull private final String myName;
    @NotNull private final String myUrl;

    public Branch(@NotNull String url) {
      myName = Url.tail(url);
      myUrl = url;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public String getUrl() {
      return myUrl;
    }

    @Override
    public String toString() {
      return myName;
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
