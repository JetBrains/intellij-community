// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class SvnIntegrateChangesActionPerformer implements SelectBranchPopup.BranchSelectedCallback {
  private final SvnVcs myVcs;
  @NotNull private final MergerFactory myMergerFactory;

  private final Url myCurrentBranch;

  public SvnIntegrateChangesActionPerformer(final Project project, final Url currentBranchUrl, @NotNull MergerFactory mergerFactory) {
    myVcs = SvnVcs.getInstance(project);
    myCurrentBranch = currentBranchUrl;
    myMergerFactory = mergerFactory;
  }

  public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, @NotNull Url url, final long revision) {
    onBranchSelected(url, null, null);
  }

  public void onBranchSelected(@NotNull Url url, @Nullable String selectedLocalBranchPath, @Nullable String dialogTitle) {
    if (myCurrentBranch.equals(url)) {
      showSameSourceAndTargetMessage();
    }
    else {
      Pair<WorkingCopyInfo, Url> pair = selectWorkingCopy(url, selectedLocalBranchPath, dialogTitle);

      if (pair != null) {
        runIntegrate(url, pair.first, pair.second);
      }
    }
  }

  @Nullable
  private Pair<WorkingCopyInfo, Url> selectWorkingCopy(@NotNull Url url,
                                                       @Nullable String selectedLocalBranchPath,
                                                       @Nullable String dialogTitle) {
    return IntegratedSelectedOptionsDialog
      .selectWorkingCopy(myVcs.getProject(), myCurrentBranch, url, true, selectedLocalBranchPath, dialogTitle);
  }

  private void runIntegrate(@NotNull Url url, @NotNull WorkingCopyInfo workingCopy, @NotNull Url workingCopyUrl) {
    Url sourceUrl = correctSourceUrl(url, workingCopyUrl);

    if (sourceUrl != null) {
      SvnIntegrateChangesTask integrateTask = new SvnIntegrateChangesTask(myVcs, workingCopy, myMergerFactory, sourceUrl, SvnBundle.message(
        "action.Subversion.integrate.changes.messages.title"), myVcs.getSvnConfiguration().isMergeDryRun(), myCurrentBranch.getTail());
      integrateTask.queue();
    }
  }

  @Nullable
  private Url correctSourceUrl(@NotNull Url targetUrl, @NotNull Url realTargetUrl) {
    if (isAncestor(targetUrl, realTargetUrl)) {
      try {
        return myCurrentBranch.appendPath(getRelativeUrl(targetUrl, realTargetUrl), false);
      }
      catch (SvnBindException ignored) {
      }
    }

    return null;
  }

  private static void showSameSourceAndTargetMessage() {
    Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.source.and.target.same.text"),
                             SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
  }
}
