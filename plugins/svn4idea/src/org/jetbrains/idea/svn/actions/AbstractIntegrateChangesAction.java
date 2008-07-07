package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.integrate.SelectedCommittedStuffChecker;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesActionPerformer;

public abstract class AbstractIntegrateChangesAction extends AnAction {
  private final SelectedCommittedStuffChecker myChecker;
  private final boolean myCheckUseCase;

  protected AbstractIntegrateChangesAction(final SelectedCommittedStuffChecker checker, final boolean checkUseCase) {
    myChecker = checker;
    myCheckUseCase = checkUseCase;
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

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    myChecker.execute(e);

    if (! myChecker.isValid()) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.no.available.files.text"),
                               SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
      return;
    }

    SelectBranchPopup.showForVCSRoot(project, myChecker.getRoot(),
                                     new SvnIntegrateChangesActionPerformer(project, myChecker.getSameBranch(), myChecker.createFactory()),
                                     SvnBundle.message("action.Subversion.integrate.changes.select.branch.text"));
  }
}
