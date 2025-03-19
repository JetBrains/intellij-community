// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesActionPerformer;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static org.jetbrains.idea.svn.SvnBundle.message;

public abstract class AbstractIntegrateChangesAction<T extends SelectedCommittedStuffChecker> extends AnAction implements DumbAware {
  private final boolean myCheckUseCase;

  protected AbstractIntegrateChangesAction(final boolean checkUseCase) {
    myCheckUseCase = checkUseCase;
  }

  protected abstract @NotNull MergerFactory createMergerFactory(final T checker);
  protected abstract @NotNull T createChecker();

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(final @NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final CommittedChangesBrowserUseCase useCase = e.getData(CommittedChangesBrowserUseCase.DATA_KEY);
    final Presentation presentation = e.getPresentation();

    if ((project == null) || (myCheckUseCase) && ((useCase == null) || (! CommittedChangesBrowserUseCase.COMMITTED.equals(useCase)))) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setText(SvnBundle.messagePointer("action.Subversion.integrate.changes.actionname"));
    presentation.setDescription(SvnBundle.messagePointer("action.Subversion.integrate.changes.description"));

    final T checker = createChecker();
    checker.execute(e);

    presentation.setVisible(true);
    presentation.setEnabled(checker.isValid());

    if (presentation.isVisible() && presentation.isEnabled() &&
        ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      presentation.setEnabled(false);
    }

    updateWithChecker(e, checker);
  }

  protected void updateWithChecker(final AnActionEvent e, SelectedCommittedStuffChecker checker) {
  }

  protected abstract @Nullable Url getSelectedBranchUrl(SelectedCommittedStuffChecker checker);

  protected abstract @Nullable String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker);

  protected abstract @DialogTitle @Nullable String getDialogTitle();

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final T checker = createChecker();
    checker.execute(e);

    if (!checker.isValid()) {
      showErrorDialog(message("dialog.message.integrate.changes.error.no.available.files"), message("dialog.title.integrate.to.branch"));
      return;
    }

    final SvnIntegrateChangesActionPerformer changesActionPerformer =
      new SvnIntegrateChangesActionPerformer(project, checker.getSameBranch(), createMergerFactory(checker));

    Url selectedBranchUrl = getSelectedBranchUrl(checker);
    if (selectedBranchUrl == null) {
      SelectBranchPopup.showForBranchRoot(project, checker.getRoot(), changesActionPerformer,
                                          message("popup.title.select.branch.to.integrate.to"));
    } else {
      changesActionPerformer.onBranchSelected(selectedBranchUrl, getSelectedBranchLocalPath(checker), getDialogTitle());
    }
  }
}
