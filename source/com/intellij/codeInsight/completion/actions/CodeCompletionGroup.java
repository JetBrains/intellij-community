
package com.intellij.codeInsight.completion.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class CodeCompletionGroup extends DefaultActionGroup {
  public CodeCompletionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null){
      presentation.setEnabled(false);
      return;
    }

    VirtualFile vFile = file.getVirtualFile();
    FileType type = vFile != null ? FileTypeManager.getInstance().getFileTypeByFile(vFile) : null;
    if (StdFileTypes.JAVA != type && StdFileTypes.JSP != type &&  StdFileTypes.XML != type){
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }
}