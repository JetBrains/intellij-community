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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public class RootUrlInfo implements RootUrlPair {
  private final SVNURL myRepositoryUrlUrl;
  private final String myRepositoryUrl;
  private final SVNURL myAbsoluteUrlAsUrl;
  private final WorkingCopyFormat myFormat;
  private boolean myRepoSupportsMergeInfo;

  private final File myIoFile;
  private final VirtualFile myVfile;
  // vcs root
  private final VirtualFile myRoot;
  private NestedCopyType myType;

  public RootUrlInfo(final SVNURL repositoryUrl, final SVNURL absoluteUrlAsUrl, final WorkingCopyFormat format, final VirtualFile vfile,
                     final VirtualFile root, boolean repoSupportsMergeInfo) {
    myRepositoryUrlUrl = repositoryUrl;
    myFormat = format;
    myVfile = vfile;
    myRoot = root;
    myRepoSupportsMergeInfo = repoSupportsMergeInfo;
    myIoFile = new File(myVfile.getPath());
    final String asString = repositoryUrl.toString();
    myRepositoryUrl = asString.endsWith("/") ? asString.substring(0, asString.length() - 1) : asString;
    myAbsoluteUrlAsUrl = absoluteUrlAsUrl;
    //myType = NestedCopyType.inner;  // default
  }

  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  public SVNURL getRepositoryUrlUrl() {
    return myRepositoryUrlUrl;
  }

  public String getAbsoluteUrl() {
    return myAbsoluteUrlAsUrl.toString();
  }

  public SVNURL getAbsoluteUrlAsUrl() {
    return myAbsoluteUrlAsUrl;
  }

  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  public File getIoFile() {
    return myIoFile;
  }

  // vcs root
  public VirtualFile getRoot() {
    return myRoot;
  }

  public VirtualFile getVirtualFile() {
    return myVfile;
  }

  public String getUrl() {
    return myAbsoluteUrlAsUrl.toString();
  }

  public NestedCopyType getType() {
    return myType;
  }

  public void setType(NestedCopyType type) {
    myType = type;
  }

  public boolean isRepoSupportsMergeInfo() {
    return myRepoSupportsMergeInfo;
  }

  public void setRepoSupportsMergeInfo(boolean repoSupportsMergeInfo) {
    myRepoSupportsMergeInfo = repoSupportsMergeInfo;
  }
}
