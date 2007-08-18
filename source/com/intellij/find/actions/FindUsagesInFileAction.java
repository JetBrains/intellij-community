package com.intellij.find.actions;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.lang.findUsages.EmptyFindUsagesProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;

public class FindUsagesInFileAction extends AnAction {

  public FindUsagesInFileAction() {
    setInjectedContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if(project==null){
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = (UsageTarget[])dataContext.getData(UsageView.USAGE_TARGETS);
    if (usageTargets != null) {
      FileEditor fileEditor = DataKeys.FILE_EDITOR.getData(dataContext);
      if (fileEditor != null) {
        usageTargets[0].findUsagesInEditor(fileEditor);
      }
    }
    else {
      Messages.showMessageDialog(
        project,
        FindBundle.message("find.no.usages.at.cursor.error"),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  public void update(AnActionEvent event){
    updateFindUsagesAction(event);
  }

  private static boolean isEnabled(DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

      return file != null && !(file.getLanguage().getFindUsagesProvider() instanceof EmptyFindUsagesProvider);
    }
    else {
      UsageTarget[] target = (UsageTarget[])dataContext.getData(UsageView.USAGE_TARGETS);
      return target != null && target.length > 0;
    }
  }

  public static void updateFindUsagesAction(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    boolean enabled = isEnabled(dataContext);
    presentation.setVisible(true);
    presentation.setEnabled(enabled);
  }
}
