package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesActionPerformer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractIntegrateChangesAction extends AnAction {
  protected final SelectedCommittedStuffChecker myChecker;
  private final boolean myCheckUseCase;

  protected AbstractIntegrateChangesAction(final SelectedCommittedStuffChecker checker, final boolean checkUseCase) {
    myChecker = checker;
    myCheckUseCase = checkUseCase;
  }

  public List<CommittedChangeList> getSelectedLists() {
    return myChecker.getSelectedLists();
  }

  public void update(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final CommittedChangesBrowserUseCase useCase = (CommittedChangesBrowserUseCase) e.getDataContext().
        getData(CommittedChangesBrowserUseCase.CONTEXT_NAME);
    final Presentation presentation = e.getPresentation();

    if ((project == null) || (myCheckUseCase) && ((useCase == null) || (! CommittedChangesBrowserUseCase.COMMITTED.equals(useCase)))) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setText(SvnBundle.message("action.Subversion.integrate.changes.actionname"));
    presentation.setDescription(SvnBundle.message("action.Subversion.integrate.changes.description"));

    myChecker.execute(e);

    presentation.setVisible(true);
    presentation.setEnabled(myChecker.isValid());

    if (presentation.isVisible() && presentation.isEnabled() &&
        ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      presentation.setEnabled(false);
    }
  }

  @Nullable
  protected abstract String getSelectedBranchUrl();
  @Nullable
  protected abstract String getSelectedBranchLocalPath();
  @Nullable
  protected abstract String getDialogTitle();

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    myChecker.execute(e);

    if (! myChecker.isValid()) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.no.available.files.text"),
                               SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
      return;
    }

    final SvnIntegrateChangesActionPerformer changesActionPerformer =
      new SvnIntegrateChangesActionPerformer(project, myChecker.getSameBranch(), myChecker.createFactory());

    final String selectedBranchUrl = getSelectedBranchUrl();
    if (selectedBranchUrl == null) {
      SelectBranchPopup.showForBranchRoot(project, myChecker.getRoot(), changesActionPerformer,
                                       SvnBundle.message("action.Subversion.integrate.changes.select.branch.text"));
    } else {
      changesActionPerformer.onBranchSelected(selectedBranchUrl, getSelectedBranchLocalPath(), getDialogTitle());
    }
  }
}
