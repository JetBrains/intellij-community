package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.io.FileNotFoundException;

public class ExportToHTMLAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    try {
      ExportToHTMLManager.executeExport(dataContext);
    }
    catch (FileNotFoundException ex) {
      JOptionPane.showMessageDialog(null, CodeEditorBundle.message("file.not.found", ex.getMessage()),
                                    CommonBundle.getErrorTitle(), JOptionPane.ERROR_MESSAGE);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    PsiElement psiElement = DataKeys.PSI_ELEMENT.getData(dataContext);
    if(psiElement instanceof PsiDirectory) {
      presentation.setEnabled(true);
      return;
    }
    PsiFile psiFile = DataKeys.PSI_FILE.getData(dataContext);
    presentation.setEnabled(psiFile != null);
  }

}