package com.intellij.ide.actions;

import com.intellij.find.FindManager;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public class SearchAgainAction extends AnAction {
  public SearchAgainAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FileEditor editor = (FileEditor)dataContext.getData(DataConstants.FILE_EDITOR);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
        project, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          if(FindManager.getInstance(project).findNextUsageInEditor(editor)) {
            return;
          }
          FindUtil.searchAgain(project, editor);
        }
      },
      "Find Next",
      null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    FileEditor editor = (FileEditor)dataContext.getData(DataConstants.FILE_EDITOR);
    presentation.setEnabled(editor != null);
  }
}
