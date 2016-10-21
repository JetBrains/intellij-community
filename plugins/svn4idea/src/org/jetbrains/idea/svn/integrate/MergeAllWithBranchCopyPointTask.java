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

import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MergeAllWithBranchCopyPointTask extends BaseMergeTask {

  @Nullable private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;
  private final boolean mySupportsMergeInfo;

  public MergeAllWithBranchCopyPointTask(@NotNull QuickMerge mergeProcess) {
    super(mergeProcess, "merge all", Where.AWT);
    myCopyPoint = null;
    mySupportsMergeInfo = true;
  }

  public MergeAllWithBranchCopyPointTask(@NotNull QuickMerge mergeProcess,
                                         @NotNull SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                                         boolean supportsMergeInfo) {
    super(mergeProcess, "merge all", Where.AWT);
    myCopyPoint = copyPoint;
    mySupportsMergeInfo = supportsMergeInfo;
  }

  @Override
  public void run() {
    boolean reintegrate = myCopyPoint != null && myCopyPoint.isInvertedSense();

    if (reintegrate && !myInteraction.shouldReintegrate(myCopyPoint.inverted().getTarget())) {
      end();
    }
    else {
      MergerFactory mergerFactory = createBranchMergerFactory(reintegrate);
      String title = "Merging all from " + myMergeContext.getBranchName() + (reintegrate ? " (reintegrate)" : "");

      merge(title, mergerFactory, null);
    }
  }

  @NotNull
  private MergerFactory createBranchMergerFactory(boolean reintegrate) {
    return (vcs, target, handler, currentBranchUrl, branchName) -> {
      long revision = myCopyPoint != null
                      ? reintegrate ? myCopyPoint.getWrapped().getTargetRevision() : myCopyPoint.getWrapped().getSourceRevision()
                      : -1;

      return new BranchMerger(vcs, currentBranchUrl, myMergeContext.getWcInfo().getPath(), handler, reintegrate,
                              myMergeContext.getBranchName(), revision, mySupportsMergeInfo);
    };
  }
}
