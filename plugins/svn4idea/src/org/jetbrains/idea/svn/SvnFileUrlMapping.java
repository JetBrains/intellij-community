/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

public interface SvnFileUrlMapping extends AbstractVcs.RootsConvertor {
  @Nullable
  SVNURL getUrlForFile(@NotNull File file);

  @Nullable
  File getLocalPath(@NotNull SVNURL url);

  @Nullable
  RootUrlInfo getWcRootForUrl(@NotNull SVNURL url);

  @NotNull
  List<RootUrlInfo> getAllWcInfos();

  @Nullable
  RootUrlInfo getWcRootForFilePath(@NotNull File file);

  @NotNull
  List<RootUrlInfo> getErrorRoots();

  @NotNull
  VirtualFile[] getNotFilteredRoots();

  boolean isEmpty();
}
