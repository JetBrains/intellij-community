/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.integrate.MergeContext;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class OneShotMergeInfoHelper implements MergeChecker {

  @NotNull private final OneRecursiveShotMergeInfoWorker myWorker;
  @NotNull private final Map<Long, Collection<String>> myPartiallyMerged;

  public OneShotMergeInfoHelper(@NotNull MergeContext mergeContext) {
    myWorker = new OneRecursiveShotMergeInfoWorker(mergeContext);
    myPartiallyMerged = ContainerUtil.newHashMap();
  }

  public void prepare() throws VcsException {
    myWorker.prepare();
  }

  @Nullable
  public Collection<String> getNotMergedPaths(long number) {
    return myPartiallyMerged.get(number);
  }

  @NotNull
  public SvnMergeInfoCache.MergeCheckResult checkList(@NotNull SvnChangeList changeList) {
    Set<String> notMergedPaths = ContainerUtil.newHashSet();
    boolean hasMergedPaths = false;

    for (String path : changeList.getAffectedPaths()) {
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (myWorker.isMerged(path, changeList.getNumber())) {
        case MERGED:
          hasMergedPaths = true;
          break;
        case NOT_MERGED:
          notMergedPaths.add(path);
          break;
      }
    }

    if (hasMergedPaths && !notMergedPaths.isEmpty()) {
      myPartiallyMerged.put(changeList.getNumber(), notMergedPaths);
    }

    return notMergedPaths.isEmpty()
           ? hasMergedPaths ? SvnMergeInfoCache.MergeCheckResult.MERGED : SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS
           : SvnMergeInfoCache.MergeCheckResult.NOT_MERGED;
  }
}
