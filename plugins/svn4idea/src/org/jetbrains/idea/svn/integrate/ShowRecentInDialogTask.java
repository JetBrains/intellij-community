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
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.dialogs.LoadRecentBranchRevisions;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.MergeDialogI;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class ShowRecentInDialogTask extends BaseMergeTask {
  private final LoadRecentBranchRevisions myLoader;

  public ShowRecentInDialogTask(@NotNull MergeContext mergeContext,
                                @NotNull QuickMergeInteraction interaction,
                                LoadRecentBranchRevisions loader) {
    super(mergeContext, interaction, "", Where.AWT);
    myLoader = loader;
  }

  @Override
  public void run(ContinuationContext context) {
    final PairConsumer<Long, MergeDialogI> loader = new PairConsumer<Long, MergeDialogI>() {
      @Override
      public void consume(Long bunchSize, final MergeDialogI dialog) {
        final LoadRecentBranchRevisions loader =
          new LoadRecentBranchRevisions(myMergeContext, dialog.getLastNumber(), bunchSize.intValue());
        final TaskDescriptor updater = new TaskDescriptor("", Where.AWT) {
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
        final Continuation fragmented = Continuation.createFragmented(myMergeContext.getProject(), true);
        fragmented.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
          @Override
          public void consume(VcsException e) {
            PopupUtil.showBalloonForActiveComponent(e.getMessage() == null ? e.getClass().getName() : e.getMessage(), MessageType.ERROR);
          }
        });
        fragmented.run(loader, updater);
      }
    };
    final List<CommittedChangeList> lists = myInteraction.showRecentListsForSelection(myLoader.getCommittedChangeLists(),
                                                                                      myMergeContext.getTitle(), myLoader.getHelper(),
                                                                                      loader, myLoader.isLastLoaded());

    if (lists != null && !lists.isEmpty()) {
      final MergerFactory factory = new ChangeListsMergerFactory(lists) {
        @Override
        public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl, String branchName) {
          return new GroupMerger(vcs, lists, target, handler, currentBranchUrl, branchName, false, false, false);
        }
      };
      // fictive branch point, just for
      final SvnBranchPointsCalculator.BranchCopyData copyData =
        new SvnBranchPointsCalculator.BranchCopyData(myMergeContext.getWcInfo().getUrl().toString(), -1, myMergeContext.getSourceUrl(),
                                                     -1);
      context.next(new LocalChangesPromptTask(myMergeContext, myInteraction,
                                              false, lists, new SvnBranchPointsCalculator.WrapperInvertor(false, copyData)
                   ),
                   new MergeTask(myMergeContext, myInteraction, factory, myMergeContext.getTitle())
      );
    }
    else {
      context.cancelEverything();
    }
  }
}
