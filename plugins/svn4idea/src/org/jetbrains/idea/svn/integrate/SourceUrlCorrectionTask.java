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

import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
* @author Konstantin Kolosovsky.
*/
public class SourceUrlCorrectionTask extends BaseMergeTask {

  public SourceUrlCorrectionTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "Checking branch", Where.POOLED);
  }

  @Override
  public void run(ContinuationContext continuationContext) {
    final SVNURL branch =
      SvnBranchConfigurationManager.getInstance(myMergeContext.getProject()).getSvnBranchConfigManager().getWorkingBranchWithReload(
        myMergeContext.getWcInfo().getUrl(), myMergeContext.getRoot());
    if (branch != null && (!myMergeContext.getWcInfo().getUrl().equals(branch))) {
      final String branchString = branch.toString();
      if (SVNPathUtil.isAncestor(branchString, myMergeContext.getWcInfo().getRootUrl())) {
        final String subPath = SVNPathUtil.getRelativePath(branchString, myMergeContext.getWcInfo().getRootUrl());
        myMergeContext.setSourceUrl(SVNPathUtil.append(myMergeContext.getSourceUrl(), subPath));
      }
    }
  }
}
