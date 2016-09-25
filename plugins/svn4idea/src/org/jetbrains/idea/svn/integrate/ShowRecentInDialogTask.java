/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class ShowRecentInDialogTask extends BaseMergeTask {

  @NotNull private final LoadRecentBranchRevisions myInitialChangeListsLoader;

  public ShowRecentInDialogTask(@NotNull MergeContext mergeContext,
                                @NotNull QuickMergeInteraction interaction,
                                @NotNull LoadRecentBranchRevisions initialChangeListsLoader) {
    super(mergeContext, interaction, "", Where.AWT);

    myInitialChangeListsLoader = initialChangeListsLoader;
  }

  @Override
  public void run(ContinuationContext context) {
    List<CommittedChangeList> lists = myInteraction
      .showRecentListsForSelection(myInitialChangeListsLoader.getCommittedChangeLists(), myInitialChangeListsLoader.getHelper(),
                                   myInitialChangeListsLoader.isLastLoaded());

    if (!lists.isEmpty()) {
      runChangeListsMerge(context, lists, createBranchCopyPoint(), myMergeContext.getTitle());
    }
    else {
      context.cancelEverything();
    }
  }

  @NotNull
  private SvnBranchPointsCalculator.WrapperInvertor createBranchCopyPoint() {
    return new SvnBranchPointsCalculator.WrapperInvertor(false, new SvnBranchPointsCalculator.BranchCopyData(
      myMergeContext.getWcInfo().getUrl().toString(), -1, myMergeContext.getSourceUrl(), -1));
  }
}
