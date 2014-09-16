/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

public class SvnIntegrateChangesActionPerformer implements SelectBranchPopup.BranchSelectedCallback {
  private final SvnVcs myVcs;
  @NotNull private final MergerFactory myMergerFactory;

  private final SVNURL myCurrentBranch;

  public SvnIntegrateChangesActionPerformer(final Project project, final SVNURL currentBranchUrl, @NotNull MergerFactory mergerFactory) {
    myVcs = SvnVcs.getInstance(project);
    myCurrentBranch = currentBranchUrl;
    myMergerFactory = mergerFactory;
  }

  public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, final String url, final long revision) {
    onBranchSelected(url, null, null);
  }

  public void onBranchSelected(String url, @Nullable String selectedLocalBranchPath, @Nullable String dialogTitle) {
    if (myCurrentBranch.toString().equals(url)) {
      showSameSourceAndTargetMessage();
    }
    else {
      Pair<WorkingCopyInfo, SVNURL> pair = selectWorkingCopy(url, selectedLocalBranchPath, dialogTitle);

      if (pair != null) {
        runIntegrate(url, pair.first, pair.second);
      }
    }
  }

  @Nullable
  private Pair<WorkingCopyInfo, SVNURL> selectWorkingCopy(String url,
                                                          @Nullable String selectedLocalBranchPath,
                                                          @Nullable String dialogTitle) {
    return IntegratedSelectedOptionsDialog
      .selectWorkingCopy(myVcs.getProject(), myCurrentBranch, url, true, selectedLocalBranchPath, dialogTitle);
  }

  private void runIntegrate(@NotNull String url, @NotNull WorkingCopyInfo workingCopy, @NotNull SVNURL workingCopyUrl) {
    SVNURL sourceUrl = correctSourceUrl(url, workingCopyUrl.toString());

    if (sourceUrl != null) {
      SvnIntegrateChangesTask integrateTask =
        new SvnIntegrateChangesTask(myVcs, workingCopy, myMergerFactory, sourceUrl, SvnBundle.message(
          "action.Subversion.integrate.changes.messages.title"), myVcs.getSvnConfiguration().isMergeDryRun(),
                                    SVNPathUtil.tail(myCurrentBranch.toString()));

      integrateTask.queue();
    }
  }

  @Nullable
  private SVNURL correctSourceUrl(@NotNull String targetUrl, @NotNull String realTargetUrl) {
    try {
      if (realTargetUrl.length() > targetUrl.length()) {
        if (realTargetUrl.startsWith(targetUrl)) {
          return myCurrentBranch.appendPath(realTargetUrl.substring(targetUrl.length()), true);
        }
      }
      else if (realTargetUrl.equals(targetUrl)) {
        return myCurrentBranch;
      }
    }
    catch (SVNException e) {
      // tracked by return value
    }
    return null;
  }

  private static void showSameSourceAndTargetMessage() {
    Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.source.and.target.same.text"),
                             SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
  }
}
