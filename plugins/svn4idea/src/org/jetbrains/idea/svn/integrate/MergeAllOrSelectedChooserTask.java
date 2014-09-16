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
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeAllOrSelectedChooserTask extends BaseMergeTask {

  public MergeAllOrSelectedChooserTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "merge source selector", Where.AWT);
  }

  @Override
  public void run(ContinuationContext context) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myInteraction.selectMergeVariant()) {
      case all:
        context.next(getMergeAllTasks());
        break;
      case showLatest:
        LoadRecentBranchRevisions loader = new LoadRecentBranchRevisions(myMergeContext, -1);
        ShowRecentInDialogTask dialog = new ShowRecentInDialogTask(myMergeContext, myInteraction, loader);

        context.next(loader, dialog);
        break;
      case select:
        MergeCalculatorTask calculator = getMergeCalculatorTask(context);

        if (calculator != null) {
          context.next(getCalculateFirstCopyPointTask(calculator), calculator);
        }
        break;
    }
  }

  @NotNull
  private TaskDescriptor getCalculateFirstCopyPointTask(@NotNull MergeCalculatorTask mergeCalculator) {
    return myMergeContext.getVcs().getSvnBranchPointsCalculator()
      .getFirstCopyPointTask(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getWcInfo().getRootUrl(),
                             myMergeContext.getSourceUrl(), mergeCalculator);
  }

  @Nullable
  private MergeCalculatorTask getMergeCalculatorTask(@NotNull ContinuationContext context) {
    MergeCalculatorTask result = null;

    try {
      result = new MergeCalculatorTask(myMergeContext, myInteraction);
    }
    catch (VcsException e) {
      finishWithError(context, e.getMessage(), true);
    }

    return result;
  }
}
