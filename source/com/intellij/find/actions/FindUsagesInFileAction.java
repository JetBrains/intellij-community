package com.intellij.find.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeSupportCapabilities;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;

public class FindUsagesInFileAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if(project==null){
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    UsageTarget[] usageTargets = (UsageTarget[])dataContext.getData(UsageView.USAGE_TARGETS);
    if (usageTargets != null) {
      usageTargets[0].findUsagesInEditor((FileEditor)dataContext.getData(DataConstants.FILE_EDITOR));
    }
    else {
      Messages.showMessageDialog(
        project,
        "Cannot search for usages.\nPosition to an element which usages you wish to find and try again.",
        "Error",
        Messages.getErrorIcon()
      );
    }
  }

  public void update(AnActionEvent event){
    updateFindUsagesAction(event);

  }

  public static void updateFindUsagesAction(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      boolean enabled = file instanceof PsiJavaFile || file instanceof JspFile || file instanceof XmlFile;
      if (!enabled && file!=null) {
        FileTypeSupportCapabilities capabilities = file.getFileType().getSupportCapabilities();
        enabled = capabilities!=null && capabilities.hasFindUsages();
      }
      presentation.setVisible(enabled);
      presentation.setEnabled(enabled);
    }
    else {
      UsageTarget[] target = (UsageTarget[])dataContext.getData(UsageView.USAGE_TARGETS);
      presentation.setVisible(true);
      presentation.setEnabled(target != null && target.length > 0);
    }
  }
}
