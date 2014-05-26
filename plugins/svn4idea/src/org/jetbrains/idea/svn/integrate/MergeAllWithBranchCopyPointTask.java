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
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.BranchMerger;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeAllWithBranchCopyPointTask extends BaseMergeTask
  implements Consumer<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> {

  private final AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> myData;

  public MergeAllWithBranchCopyPointTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "merge all", Where.AWT);
    myData = new AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>>();
  }

  @Override
  public void consume(TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> value) {
    myData.set(value);
  }

  @Override
  public void run(ContinuationContext context) {
    SvnBranchPointsCalculator.WrapperInvertor invertor;
    try {
      final TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>
        transparentlyFailedValueI = myData.get();
      if (transparentlyFailedValueI == null) {
        finishWithError(context, "Merge start wasn't found", true);
        return;
      }
      invertor = transparentlyFailedValueI.get();
    }
    catch (VcsException e) {
      finishWithError(context, "Merge start wasn't found", Collections.singletonList(e));
      return;
    }
    if (invertor == null) {
      finishWithError(context, "Merge start wasn't found", true);
      return;
    }
    final boolean reintegrate = invertor.isInvertedSense();
    if (reintegrate && (!myInteraction.shouldReintegrate(myMergeContext.getSourceUrl(), invertor.inverted().getTarget()))) {
      context.cancelEverything();
      return;
    }
    final MergerFactory mergerFactory = createBranchMergerFactory(reintegrate, invertor);

    final String title = "Merging all from " + myMergeContext.getBranchName() + (reintegrate ? " (reintegrate)" : "");
    context.next(new MergeTask(myMergeContext, myInteraction, mergerFactory, title));
  }

  private MergerFactory createBranchMergerFactory(final boolean reintegrate,
                                                  final SvnBranchPointsCalculator.WrapperInvertor invertor) {
    return new MergerFactory() {
      public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl, String branchName) {
        return new BranchMerger(vcs, currentBranchUrl, myMergeContext.getWcInfo().getPath(), handler,
                                reintegrate, myMergeContext.getBranchName(),
                                reintegrate ? invertor.getWrapped().getTargetRevision() : invertor.getWrapped().getSourceRevision());
      }
    };
  }
}
