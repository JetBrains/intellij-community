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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
* @author Konstantin Kolosovsky.
*/
public class SourceUrlCorrectionTask extends BaseMergeTask {

  public SourceUrlCorrectionTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "Checking branch", Where.POOLED);
  }

  /**
   * Updates source url to corresponding source branch sub-path when working copy is checked out not from other branch root but from its
   * sub-path. Example:
   * - working copy root url - ".../trunk/folder1"
   * - source url/branch - ".../branches/branch1"
   * => source url will be updated to ".../branches/branch1/folder1" - as only changes from this sub-folder could be merged to working copy.
   */
  @Override
  public void run(ContinuationContext context) {
    SVNURL branch = getWorkingBranch();

    if (branch != null && !isWorkingCopyRootUrl(branch)) {
      String branchRelativePath = SVNPathUtil.getRelativePath(branch.toString(), myMergeContext.getWcInfo().getRootUrl());

      if (!StringUtil.isEmpty(branchRelativePath)) {
        myMergeContext.setSourceUrl(SVNPathUtil.append(myMergeContext.getSourceUrl(), branchRelativePath));
      }
    }
  }

  @Nullable
  private SVNURL getWorkingBranch() {
    return SvnBranchConfigurationManager.getInstance(myMergeContext.getProject()).getSvnBranchConfigManager().getWorkingBranchWithReload(
      myMergeContext.getWcInfo().getUrl(), myMergeContext.getRoot());
  }

  private boolean isWorkingCopyRootUrl(@NotNull SVNURL branch) {
    return myMergeContext.getWcInfo().getUrl().equals(branch);
  }
}
