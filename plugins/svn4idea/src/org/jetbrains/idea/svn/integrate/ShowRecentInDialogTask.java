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

import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.MergeDialogI;

import java.util.Collections;
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
    List<CommittedChangeList> lists = myInteraction.showRecentListsForSelection(myInitialChangeListsLoader.getCommittedChangeLists(),
                                                                                myMergeContext.getTitle(),
                                                                                myInitialChangeListsLoader.getHelper(),
                                                                                createMoreChangeListsLoader(),
                                                                                myInitialChangeListsLoader.isLastLoaded());

    if (lists != null && !lists.isEmpty()) {
      runChangeListsMerge(context, lists, createBranchCopyPoint(), myMergeContext.getTitle());
    }
    else {
      context.cancelEverything();
    }
  }

  @NotNull
  private PairConsumer<Long, MergeDialogI> createMoreChangeListsLoader() {
    return new PairConsumer<Long, MergeDialogI>() {

      @Override
      public void consume(@NotNull Long bunchSize, @NotNull MergeDialogI dialog) {
        LoadRecentBranchRevisions loader = new LoadRecentBranchRevisions(myMergeContext, dialog.getLastNumber(), bunchSize.intValue());
        TaskDescriptor updater = createUpdateDialogTask(dialog, loader);

        Continuation fragmented = Continuation.createFragmented(myMergeContext.getProject(), true);
        fragmented.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
          @Override
          public void consume(VcsException e) {
            PopupUtil.showBalloonForActiveComponent(e.getMessage() == null ? e.getClass().getName() : e.getMessage(), MessageType.ERROR);
          }
        });
        fragmented.run(loader, updater);
      }
    };
  }

  @NotNull
  private SvnBranchPointsCalculator.WrapperInvertor createBranchCopyPoint() {
    return new SvnBranchPointsCalculator.WrapperInvertor(false, new SvnBranchPointsCalculator.BranchCopyData(
      myMergeContext.getWcInfo().getUrl().toString(), -1, myMergeContext.getSourceUrl(), -1));
  }

  @NotNull
  private static TaskDescriptor createUpdateDialogTask(@NotNull final MergeDialogI dialog,
                                                       @NotNull final LoadRecentBranchRevisions loader) {
    return new TaskDescriptor("", Where.AWT) {

      @Override
      public void run(ContinuationContext context) {
        dialog.addMoreLists(loader.getCommittedChangeLists());
        if (loader.isLastLoaded()) {
          dialog.setEverythingLoaded(true);
        }
      }

      @Override
      public void canceled() {
        dialog.addMoreLists(Collections.<CommittedChangeList>emptyList());
        dialog.setEverythingLoaded(true);
      }
    };
  }
}
