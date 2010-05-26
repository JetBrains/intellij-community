package org.jetbrains.idea.svn.mergeinfo;

import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 24.05.2010
 * Time: 23:07:06
 * To change this template use File | Settings | File Templates.
 */
public interface MergeChecker {
  void clear();

  SvnMergeInfoCache.MergeCheckResult checkList(SvnChangeList list, String branchPath);

  // if nothing, maybe all not merged or merged: here only partly not merged

  Collection<String> getNotMergedPaths(long number);
}
