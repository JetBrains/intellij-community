// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

// TODO: Probably we could utilize VcsVirtualFile instead of this class. VcsVirtualVile contains VcsFileRevision that
// TODO: provides RepositoryLocation
public class Node {

  @NotNull private final VirtualFile myFile;
  @NotNull private final Url myUrl;
  @NotNull private final Url myRepositoryUrl;
  @Nullable private final SvnBindException myError;

  public Node(@NotNull VirtualFile file, @NotNull Url url, @NotNull Url repositoryUrl) {
    this(file, url, repositoryUrl, null);
  }

  public Node(@NotNull VirtualFile file, @NotNull Url url, @NotNull Url repositoryUrl, @Nullable SvnBindException error) {
    myFile = file;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
    myError = error;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public File getIoFile() {
    return VfsUtilCore.virtualToIoFile(getFile());
  }

  @NotNull
  public Url getUrl() {
    return myUrl;
  }

  @NotNull
  public Url getRepositoryRootUrl() {
    return myRepositoryUrl;
  }

  @Nullable
  public SvnBindException getError() {
    return myError;
  }

  public boolean hasError() {
    return myError != null;
  }

  public boolean onUrl(@Nullable Url url) {
    return myUrl.equals(url);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Node node = (Node)o;

    if (myError != null ? (node.myError == null || !myError.getMessage().equals(node.myError.getMessage())) : node.myError != null) {
      return false;
    }
    if (!myFile.equals(node.myFile)) return false;
    if (!myRepositoryUrl.equals(node.myRepositoryUrl)) return false;
    if (!myUrl.equals(node.myUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myUrl.hashCode();
    result = 31 * result + myRepositoryUrl.hashCode();
    result = 31 * result + (myError != null ? myError.getMessage().hashCode() : 0);
    return result;
  }
}
