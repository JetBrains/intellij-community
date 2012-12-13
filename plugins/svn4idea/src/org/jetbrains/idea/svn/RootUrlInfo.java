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

  private final File myIoFile;
  private final VirtualFile myVfile;
  // vcs root
  private final VirtualFile myRoot;
  private NestedCopyType myType;

  public RootUrlInfo(final SVNURL repositoryUrl, final SVNURL absoluteUrlAsUrl, final WorkingCopyFormat format, final VirtualFile vfile,
                     final VirtualFile root) {
    myRepositoryUrlUrl = repositoryUrl;
    myFormat = format;
    myVfile = vfile;
    myRoot = root;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RootUrlInfo info = (RootUrlInfo)o;

    if (myAbsoluteUrlAsUrl != null ? !myAbsoluteUrlAsUrl.equals(info.myAbsoluteUrlAsUrl) : info.myAbsoluteUrlAsUrl != null) return false;
    if (myFormat != info.myFormat) return false;
    if (myIoFile != null ? !myIoFile.equals(info.myIoFile) : info.myIoFile != null) return false;
    if (myRepositoryUrlUrl != null ? !myRepositoryUrlUrl.equals(info.myRepositoryUrlUrl) : info.myRepositoryUrlUrl != null) return false;
    if (myRoot != null ? !myRoot.equals(info.myRoot) : info.myRoot != null) return false;
    if (myType != info.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRepositoryUrlUrl != null ? myRepositoryUrlUrl.hashCode() : 0;
    result = 31 * result + (myAbsoluteUrlAsUrl != null ? myAbsoluteUrlAsUrl.hashCode() : 0);
    result = 31 * result + (myFormat != null ? myFormat.hashCode() : 0);
    result = 31 * result + (myIoFile != null ? myIoFile.hashCode() : 0);
    result = 31 * result + (myRoot != null ? myRoot.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
