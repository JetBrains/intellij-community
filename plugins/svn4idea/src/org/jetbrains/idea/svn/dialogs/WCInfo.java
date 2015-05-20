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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

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
  public SVNURL getUrl() {
    return myRootInfo.getAbsoluteUrlAsUrl();
  }

  @NotNull
  public String getRootUrl() {
    return getUrl().toString();
  }

  @NotNull
  public String getRepoUrl() {
    return getRepositoryRoot();
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

  @NotNull
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
