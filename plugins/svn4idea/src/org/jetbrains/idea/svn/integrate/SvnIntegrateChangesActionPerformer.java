// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class SvnIntegrateChangesActionPerformer implements SelectBranchPopup.BranchSelectedCallback {
  private final SvnVcs myVcs;
  private final @NotNull MergerFactory myMergerFactory;

  private final Url myCurrentBranch;

  public SvnIntegrateChangesActionPerformer(final Project project, final Url currentBranchUrl, @NotNull MergerFactory mergerFactory) {
    myVcs = SvnVcs.getInstance(project);
    myCurrentBranch = currentBranchUrl;
    myMergerFactory = mergerFactory;
  }

  @Override
  public void branchSelected(final Project project, final SvnBranchConfigurationNew configuration, @NotNull Url url, final long revision) {
    onBranchSelected(url, null, null);
  }

  public void onBranchSelected(@NotNull Url url, @Nullable String selectedLocalBranchPath, @DialogTitle @Nullable String dialogTitle) {
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

  private @Nullable Pair<WorkingCopyInfo, Url> selectWorkingCopy(@NotNull Url url,
                                                                 @Nullable String selectedLocalBranchPath,
                                                                 @DialogTitle @Nullable String dialogTitle) {
    return IntegratedSelectedOptionsDialog
      .selectWorkingCopy(myVcs.getProject(), myCurrentBranch, url, true, selectedLocalBranchPath, dialogTitle);
  }

  private void runIntegrate(@NotNull Url url, @NotNull WorkingCopyInfo workingCopy, @NotNull Url workingCopyUrl) {
    Url sourceUrl = correctSourceUrl(url, workingCopyUrl);

    if (sourceUrl != null) {
      SvnIntegrateChangesTask integrateTask = new SvnIntegrateChangesTask(
        myVcs, workingCopy, myMergerFactory, sourceUrl, message("action.Subversion.integrate.changes.messages.title"),
        myVcs.getSvnConfiguration().isMergeDryRun(), myCurrentBranch.getTail()
      );
      integrateTask.queue();
    }
  }

  private @Nullable Url correctSourceUrl(@NotNull Url targetUrl, @NotNull Url realTargetUrl) {
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
    Messages.showErrorDialog(message("dialog.message.integrate.changes.error.same.source.and.target"),
                             message("dialog.title.integrate.to.branch"));
  }
}
