package com.intellij.codeEditor.printing;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class PrintAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    PrintManager.executePrint(dataContext);
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