package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;

public class JumpToLastEditAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;
    IdeDocumentHistory.getInstance(project).navigatePreviousChange();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(IdeDocumentHistory.getInstance(project).isNavigatePreviousChangeAvailable());
  }
}