package org.jetbrains.idea.svn.mergeinfo;

import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Collection;

public interface MergeChecker {
  SvnMergeInfoCache.MergeCheckResult checkList(SvnChangeList list);

  // if nothing, maybe all not merged or merged: here only partly not merged
  Collection<String> getNotMergedPaths(long number);
}
