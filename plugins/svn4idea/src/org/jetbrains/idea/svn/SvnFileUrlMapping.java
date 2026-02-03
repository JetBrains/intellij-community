// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;
import java.util.List;

public interface SvnFileUrlMapping extends AbstractVcs.RootsConvertor {
  @Nullable
  Url getUrlForFile(@NotNull File file);

  @Nullable
  File getLocalPath(@NotNull Url url);

  @Nullable
  RootUrlInfo getWcRootForUrl(@NotNull Url url);

  @NotNull
  List<RootUrlInfo> getAllWcInfos();

  @Nullable
  RootUrlInfo getWcRootForFilePath(@NotNull FilePath path);

  @NotNull
  List<RootUrlInfo> getErrorRoots();

  VirtualFile @NotNull [] getNotFilteredRoots();

  boolean isEmpty();
}
