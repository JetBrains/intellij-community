/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

public class ShowRevisionSelector extends BaseMergeTask {

  @NotNull private final List<CommittedChangeList> myChangeLists;
  @NotNull private final MergeChecker myMergeChecker;
  private final boolean myAllStatusesCalculated;
  private final boolean myAllListsLoaded;

  public ShowRevisionSelector(@NotNull QuickMerge mergeProcess,
                              @NotNull List<CommittedChangeList> changeLists,
                              @NotNull MergeChecker mergeChecker,
                              boolean allStatusesCalculated,
                              boolean allListsLoaded) {
    super(mergeProcess, "show revisions to merge", Where.AWT);

    myChangeLists = changeLists;
    myMergeChecker = mergeChecker;
    myAllStatusesCalculated = allStatusesCalculated;
    myAllListsLoaded = allListsLoaded;
  }

  @Override
  public void run() {
    SelectMergeItemsResult result =
      myInteraction.selectMergeItems(myChangeLists, myMergeChecker, myAllStatusesCalculated, myAllListsLoaded);

    switch (result.getResultCode()) {
      case cancel:
        end();
        break;
      case all:
        next(getMergeAllTasks(true));
        break;
      default:
        List<CommittedChangeList> lists = result.getSelectedLists();

        if (!lists.isEmpty()) {
          runChangeListsMerge(lists, myMergeContext.getTitle());
        }
        break;
    }
  }
}
