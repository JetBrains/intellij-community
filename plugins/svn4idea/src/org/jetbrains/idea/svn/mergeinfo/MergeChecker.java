// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Collection;

public interface MergeChecker {

  void prepare() throws VcsException;

  @NotNull
  MergeCheckResult checkList(@NotNull SvnChangeList changeList);

  // if nothing, maybe all not merged or merged: here only partly not merged
  @Nullable
  Collection<String> getNotMergedPaths(@NotNull SvnChangeList changeList);
}
