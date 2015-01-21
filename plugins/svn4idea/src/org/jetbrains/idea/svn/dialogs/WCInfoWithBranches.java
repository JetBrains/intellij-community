/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

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
      myName = SVNPathUtil.tail(url);
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
