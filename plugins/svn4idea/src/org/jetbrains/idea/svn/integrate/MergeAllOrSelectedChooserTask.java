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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.LoadRecentBranchRevisions;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.QuickMergeContentsVariants;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeAllOrSelectedChooserTask extends BaseMergeTask {
  public MergeAllOrSelectedChooserTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "merge source selector", Where.AWT);
  }

  @Override
  public void run(final ContinuationContext context) {
    final QuickMergeContentsVariants variant = myInteraction.selectMergeVariant();
    if (QuickMergeContentsVariants.cancel == variant) return;
    if (QuickMergeContentsVariants.all == variant) {
      insertMergeAll(context);
      return;
    }
    if (QuickMergeContentsVariants.showLatest == variant) {
      final LoadRecentBranchRevisions loader = new LoadRecentBranchRevisions(myMergeContext, -1);
      final ShowRecentInDialogTask dialog = new ShowRecentInDialogTask(myMergeContext, myInteraction, loader);
      context.next(loader, dialog);
      return;
    }

    final MergeCalculatorTask calculator;
    try {
      calculator = new MergeCalculatorTask(myMergeContext, myInteraction);
    }
    catch (VcsException e) {
      finishWithError(context, e.getMessage(), true);
      return;
    }
    context.next(myMergeContext.getVcs().getSvnBranchPointsCalculator()
                   .getFirstCopyPointTask(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getWcInfo().getRootUrl(),
                                          myMergeContext.getSourceUrl(), calculator), calculator);
  }
}
