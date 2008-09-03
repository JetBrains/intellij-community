package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;

public class DeleteAlreadyUnshelvedAction extends AnAction {
  private final String myText;

  public DeleteAlreadyUnshelvedAction() {
    myText = VcsBundle.message("delete.all.already.unshelved");
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    presentation.setEnabled(true);
    presentation.setVisible(true);
    presentation.setText(myText);
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final int result = Messages
      .showYesNoDialog(project, VcsBundle.message("delete.all.already.unshelved.confirmation"), myText,
                       Messages.getWarningIcon());
    if (DialogWrapper.OK_EXIT_CODE == result) {
      final ShelveChangesManager manager = ShelveChangesManager.getInstance(project);
      manager.clearRecycled();
    }
  }
}
