// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  protected abstract MergerFactory createMergerFactory(final T checker);
  @NotNull
  protected abstract T createChecker();

  @Override
  public final void update(@NotNull final AnActionEvent e) {
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

  @Nullable
  protected abstract Url getSelectedBranchUrl(SelectedCommittedStuffChecker checker);

  @Nullable
  protected abstract String getSelectedBranchLocalPath(SelectedCommittedStuffChecker checker);

  @DialogTitle
  @Nullable
  protected abstract String getDialogTitle();

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
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
