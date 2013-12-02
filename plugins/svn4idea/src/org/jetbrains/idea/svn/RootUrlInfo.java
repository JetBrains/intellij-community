/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.UriUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public class RootUrlInfo implements RootUrlPair {

  @NotNull private final String myRepositoryUrl;
  @NotNull private final WorkingCopyFormat myFormat;
  @NotNull private final Node myNode;
  // vcs root
  @NotNull private final VirtualFile myRoot;
  @Nullable private volatile NestedCopyType myType;

  public RootUrlInfo(@NotNull final Node node, @NotNull final WorkingCopyFormat format, @NotNull final VirtualFile root) {
    this(node, format, root, null);
  }

  public RootUrlInfo(@NotNull final Node node,
                     @NotNull final WorkingCopyFormat format,
                     @NotNull final VirtualFile root,
                     @Nullable final NestedCopyType type) {
    myNode = node;
    myFormat = format;
    myRoot = root;
    myRepositoryUrl = UriUtil.trimTrailingSlashes(node.getRepositoryRootUrl().toString());
    myType = type;
  }

  @NotNull
  public Node getNode() {
    return myNode;
  }

  @NotNull
  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @NotNull
  public SVNURL getRepositoryUrlUrl() {
    return myNode.getRepositoryRootUrl();
  }

  @NotNull
  public String getAbsoluteUrl() {
    return getAbsoluteUrlAsUrl().toString();
  }

  @NotNull
  public SVNURL getAbsoluteUrlAsUrl() {
    return myNode.getUrl();
  }

  @NotNull
  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  @NotNull
  public File getIoFile() {
    return myNode.getIoFile();
  }

  @NotNull
  public String getPath() {
    return getIoFile().getAbsolutePath();
  }

  // vcs root
  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myNode.getFile();
  }

  @NotNull
  public String getUrl() {
    return getAbsoluteUrl();
  }

  @Nullable
  public NestedCopyType getType() {
    return myType;
  }

  public void setType(@Nullable NestedCopyType type) {
    myType = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RootUrlInfo info = (RootUrlInfo)o;

    if (myFormat != info.myFormat) return false;
    if (!myNode.equals(info.myNode)) return false;
    if (!myRoot.equals(info.myRoot)) return false;
    if (myType != info.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFormat.hashCode();
    result = 31 * result + myNode.hashCode();
    result = 31 * result + myRoot.hashCode();
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
