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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

class Node {

  @NotNull private final VirtualFile myFile;
  @Nullable private final SVNURL myUrl;
  @Nullable private final SVNURL myRepositoryUrl;

  Node(@NotNull VirtualFile file) {
    this(file, null);
  }

  Node(@NotNull VirtualFile file, @Nullable SVNURL url) {
    this(file, url, null);
  }

  Node(@NotNull VirtualFile file, @Nullable SVNURL url, @Nullable SVNURL repositoryUrl) {
    myFile = file;
    myUrl = url;
    myRepositoryUrl = repositoryUrl;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public SVNURL getUrl() {
    return myUrl;
  }

  public boolean inVcs() {
    return myUrl != null;
  }

  public boolean sameVcsItem(@NotNull Node node) {
    //noinspection ConstantConditions
    return inVcs() && node.inVcs() && myUrl.equals(node.myUrl);
  }

  @Nullable
  public SVNURL getRepositoryRootUrl() {
    return myRepositoryUrl;
  }

  @NotNull
  public Node append(@NotNull VirtualFile childFile) {
    return new Node(childFile, myUrl != null ? SvnUtil.append(myUrl, childFile.getName()) : null);
  }
}
