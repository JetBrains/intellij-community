package org.jetbrains.idea.svn17.mergeinfo;

import org.jetbrains.idea.svn17.history.SvnChangeList;

import java.util.Collection;

public interface MergeChecker {
  SvnMergeInfoCache17.MergeCheckResult checkList(SvnChangeList list);

  // if nothing, maybe all not merged or merged: here only partly not merged
  Collection<String> getNotMergedPaths(long number);
}
