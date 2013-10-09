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
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

public class WCInfo implements WCPaths {
  private final boolean myIsWcRoot;
  private final SVNDepth myStickyDepth;
  private final RootUrlInfo myRootInfo;

  public WCInfo(@NotNull RootUrlInfo rootInfo, boolean isWcRoot, SVNDepth stickyDepth) {
    myRootInfo = rootInfo;
    myIsWcRoot = isWcRoot;
    myStickyDepth = stickyDepth;
  }

  public SVNDepth getStickyDepth() {
    return myStickyDepth;
  }

  public String getPath() {
    return myRootInfo.getPath();
  }

  public VirtualFile getVcsRoot() {
    return null;
  }

  public SVNURL getUrl() {
    return myRootInfo.getAbsoluteUrlAsUrl();
  }

  public String getRootUrl() {
    return getUrl().toString();
  }

  public String getRepoUrl() {
    return getRepositoryRoot();
  }

  public RootUrlInfo getRootInfo() {
    return myRootInfo;
  }

  public boolean hasError() {
    return getRootInfo().getNode().hasError();
  }

  public String getErrorMessage() {
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    SVNException error = getRootInfo().getNode().getError();

    return error != null ? error.getMessage() : "";
  }

  public WorkingCopyFormat getFormat() {
    return myRootInfo.getFormat();
  }

  public String getRepositoryRoot() {
    return myRootInfo.getRepositoryUrl();
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo)) return false;

    final WCInfo wcInfo = (WCInfo)o;
    final String path = getPath();

    if (path != null ? !path.equals(wcInfo.getPath()) : wcInfo.getPath() != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    final String path = getPath();

    return (path != null ? path.hashCode() : 0);
  }

  public NestedCopyType getType() {
    return myRootInfo.getType();
  }
}
