package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Collection;

public interface MergeChecker {

  void prepare() throws VcsException;

  @NotNull
  SvnMergeInfoCache.MergeCheckResult checkList(@NotNull SvnChangeList changeList);

  // if nothing, maybe all not merged or merged: here only partly not merged
  @Nullable
  Collection<String> getNotMergedPaths(@NotNull SvnChangeList changeList);
}
