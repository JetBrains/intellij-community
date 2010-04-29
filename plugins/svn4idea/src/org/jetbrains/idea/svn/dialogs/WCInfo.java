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
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;

public class WCInfo implements WCPaths {
  private final String myPath;
  private final SVNURL myUrl;
  private final WorkingCopyFormat myFormat;
  private final String myRepositoryRoot;
  private final boolean myIsWcRoot;
  private final NestedCopyType myType;
  private final SVNDepth myStickyDepth;

  public WCInfo(final String path, final SVNURL url, final WorkingCopyFormat format, final String repositoryRoot, final boolean isWcRoot,
                final NestedCopyType type, SVNDepth stickyDepth) {
    myPath = path;
    myUrl = url;
    myFormat = format;
    myRepositoryRoot = repositoryRoot;
    myIsWcRoot = isWcRoot;
    myType = type;
    myStickyDepth = stickyDepth;
  }

  public SVNDepth getStickyDepth() {
    return myStickyDepth;
  }

  public String getPath() {
    return myPath;
  }

  public VirtualFile getVcsRoot() {
    return null;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public String getRootUrl() {
    return myUrl.toString();
  }

  public String getRepoUrl() {
    return myRepositoryRoot;
  }

  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  public String getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo)) return false;

    final WCInfo wcInfo = (WCInfo)o;

    if (myPath != null ? !myPath.equals(wcInfo.myPath) : wcInfo.myPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (myPath != null ? myPath.hashCode() : 0);
  }

  public NestedCopyType getType() {
    return myType;
  }
}
