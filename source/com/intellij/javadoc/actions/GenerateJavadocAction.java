package com.intellij.javadoc.actions;

import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

public final class GenerateJavadocAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final PsiDirectory dir = getDirectoryFromContext(dataContext);
    JavadocGenerationManager.getInstance(project).generateJavadoc(dir, dataContext);
  }

  public void update(AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(event.getDataContext().getData(DataConstants.PROJECT) != null);
  }

  private static PsiDirectory getDirectoryFromContext(final DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null){
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) return psiFile.getContainingDirectory();
    } else {
      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element != null) {
        if (element instanceof PsiDirectory) return (PsiDirectory)element;
        else{
          PsiFile psiFile = element.getContainingFile();
          if (psiFile != null) return psiFile.getContainingDirectory();
        }
      } else {
        //This is the case with GUI designer
        VirtualFile virtualFile = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
        if (virtualFile != null && virtualFile.isValid()) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
          if (psiFile != null) return psiFile.getContainingDirectory();
        }
      }
    }
    return null;
  }

}