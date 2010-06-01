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
import com.intellij.openapi.util.Pair;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNException;

import java.util.HashSet;
import java.util.Set;

public class OneShotMergeInfoHelper {
  private OneRecursiveShotMergeInfoWorker myWorker;

  public OneShotMergeInfoHelper(final Project project, final WCInfo wcInfo, final String branchPath) throws SVNException {
    myWorker = new OneRecursiveShotMergeInfoWorker(project, wcInfo, branchPath);
  }

  public void prepare() throws SVNException {
    myWorker.prepare();
  }

  public Pair<SvnMergeInfoCache.MergeCheckResult, Set<String>> checkList(final SvnChangeList list) {
    final Set<String> merged = new HashSet<String>();
    boolean somethingAvailableForMergeFound = false;

    final long number = list.getNumber();
    final Set<String> paths = new HashSet<String>(list.getAddedPaths());
    paths.addAll(list.getDeletedPaths());
    paths.addAll(list.getChangedPaths());

    for (String path : paths) {
      final SvnMergeInfoCache.MergeCheckResult pathResult = myWorker.isMerged(path, number);
      if (SvnMergeInfoCache.MergeCheckResult.MERGED.equals(pathResult)) {
        merged.add(path);
      } else if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(pathResult)) {
        somethingAvailableForMergeFound = true;
      }
    }

    return new Pair<SvnMergeInfoCache.MergeCheckResult, Set<String>>(
      SvnMergeInfoCache.MergeCheckResult.getInstance(! somethingAvailableForMergeFound), merged);
  }
}
