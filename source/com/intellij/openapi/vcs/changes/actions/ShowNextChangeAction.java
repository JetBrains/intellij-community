package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.project.Project;

/**
 * @author yole
*/
public class ShowNextChangeAction extends AnAction {
  public ShowNextChangeAction() {
    setEnabledInModalContext(true);
  }

  public void update(AnActionEvent e) {
    final DiffRequest request = e.getData(DataKeys.DIFF_REQUEST);
    if (request instanceof ChangeDiffRequest) {
      final ChangeDiffRequest changeDiffRequest = (ChangeDiffRequest)request;
      e.getPresentation().setEnabled(changeDiffRequest.getIndex() < changeDiffRequest.getChanges().length - 1);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final DiffRequest request = e.getData(DataKeys.DIFF_REQUEST);
    if (request instanceof ChangeDiffRequest) {
      final ChangeDiffRequest changeDiffRequest = (ChangeDiffRequest)request;
      ShowDiffAction.showDiffForChange(e,
                                       changeDiffRequest.getChanges(),
                                       changeDiffRequest.getIndex() + 1,
                                       project,
                                       changeDiffRequest.getActionsFactory());
    }
  }
}