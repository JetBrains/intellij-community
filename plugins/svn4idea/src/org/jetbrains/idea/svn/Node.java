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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

// TODO: Probably we could utilize VcsVirtualFile instead of this class. VcsVirtualVile contains VcsFileRevision that
// TODO: provides RepositoryLocation
public class Node {

  @NotNull private final VirtualFile myFile;
  @NotNull private final SVNURL myUrl;
  @NotNull private final SVNURL myRepositoryUrl;

  public Node(@NotNull VirtualFile file, @NotNull SVNURL url, @NotNull SVNURL repositoryUrl) {
    myFile = file;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
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
  public SVNURL getUrl() {
    return myUrl;
  }

  @NotNull
  public SVNURL getRepositoryRootUrl() {
    return myRepositoryUrl;
  }

  public boolean onUrl(@Nullable SVNURL url) {
    return myUrl.equals(url);
  }
}
