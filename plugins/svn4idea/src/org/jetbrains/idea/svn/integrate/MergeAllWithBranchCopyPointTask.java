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

import java.util.concurrent.atomic.AtomicReference;

public class MergeAllWithBranchCopyPointTask extends BaseMergeTask
  implements Consumer<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> {

  @NotNull private final AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> myData;

  public MergeAllWithBranchCopyPointTask(@NotNull QuickMerge mergeProcess) {
    super(mergeProcess, "merge all", Where.AWT);

    myData = new AtomicReference<>();
  }

  @Override
  public void consume(TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> value) {
    myData.set(value);
  }

  @Override
  public void run(ContinuationContext context) {
    TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> inverterValue = myData.get();

    if (inverterValue != null) {
      runMerge(context, inverterValue);
    }
    else {
      end(context, "Merge start wasn't found", true);
    }
  }

  private void runMerge(@NotNull ContinuationContext context,
                        @NotNull TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> inverterValue) {
    try {
      SvnBranchPointsCalculator.WrapperInvertor inverter = inverterValue.get();

      if (inverter != null) {
        runMerge(context, inverter);
      }
      else {
        end(context, "Merge start wasn't found", true);
      }
    }
    catch (VcsException e) {
      end(context, "Merge start wasn't found", e);
    }
  }

  private void runMerge(@NotNull ContinuationContext context, @NotNull SvnBranchPointsCalculator.WrapperInvertor inverter) {
    boolean reintegrate = inverter.isInvertedSense();

    if (reintegrate && !myInteraction.shouldReintegrate(inverter.inverted().getTarget())) {
      context.cancelEverything();
    }
    else {
      MergerFactory mergerFactory = createBranchMergerFactory(reintegrate, inverter);
      String title = "Merging all from " + myMergeContext.getBranchName() + (reintegrate ? " (reintegrate)" : "");

      context.next(new MergeTask(myMergeProcess, mergerFactory, title));
    }
  }

  @NotNull
  private MergerFactory createBranchMergerFactory(boolean reintegrate, @NotNull SvnBranchPointsCalculator.WrapperInvertor inverter) {
    return (vcs, target, handler, currentBranchUrl, branchName) ->
      new BranchMerger(vcs, currentBranchUrl, myMergeContext.getWcInfo().getPath(), handler, reintegrate, myMergeContext.getBranchName(),
                       reintegrate ? inverter.getWrapped().getTargetRevision() : inverter.getWrapped().getSourceRevision());
  }
}
