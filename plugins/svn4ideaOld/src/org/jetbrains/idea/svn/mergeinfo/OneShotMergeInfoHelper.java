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

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNException;

import java.util.*;

public class OneShotMergeInfoHelper implements MergeChecker {
  private OneRecursiveShotMergeInfoWorker myWorker;
  private final Map<Long, Collection<String>> myPartiallyMerged;

  public OneShotMergeInfoHelper(final Project project, final WCInfo wcInfo, final String branchPath) throws SVNException {
    myWorker = new OneRecursiveShotMergeInfoWorker(project, wcInfo, branchPath);
    myPartiallyMerged = new HashMap<Long, Collection<String>>();
  }

  public void prepare() throws SVNException {
    myWorker.prepare();
  }

  public Collection<String> getNotMergedPaths(long number) {
    return myPartiallyMerged.get(number);
  }

  public SvnMergeInfoCache.MergeCheckResult checkList(final SvnChangeList list) {
    final Set<String> notMerged = new HashSet<String>();
    boolean somethingMerged = false;

    final long number = list.getNumber();
    final Set<String> paths = new HashSet<String>(list.getAddedPaths());
    paths.addAll(list.getDeletedPaths());
    paths.addAll(list.getChangedPaths());

    for (String path : paths) {
      final SvnMergeInfoCache.MergeCheckResult pathResult = myWorker.isMerged(path, number);
      if (SvnMergeInfoCache.MergeCheckResult.MERGED.equals(pathResult)) {
        somethingMerged = true;
      } else if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(pathResult)) {
        notMerged.add(path);
      }
    }

    if (somethingMerged && (! notMerged.isEmpty())) {
      myPartiallyMerged.put(number, notMerged);
    }
    if ((! somethingMerged) && notMerged.isEmpty()) return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;
    return SvnMergeInfoCache.MergeCheckResult.getInstance(somethingMerged && notMerged.isEmpty());
  }
}
