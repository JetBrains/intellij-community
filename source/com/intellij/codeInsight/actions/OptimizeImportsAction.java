package com.intellij.codeInsight.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class OptimizeImportsAction extends AnAction {
  private static final String HELP_ID = "editing.manageImports";

  public void actionPerformed(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);

    PsiFile file = null;
    PsiDirectory dir;

    if (editor != null){
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      dir = file.getContainingDirectory();
    }
    else{
      Project projectContext = (Project)dataContext.getData(DataConstantsEx.PROJECT_CONTEXT);
      Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);

      if (projectContext != null || moduleContext != null) {
        final String text;
        if (moduleContext != null) {
          text = "Module '" + moduleContext.getName() + "'";
        }
        else {
          text = "Project '" + projectContext.getProjectFilePath() + "'";
        }
        LayoutProjectCodeDialog dialog = new LayoutProjectCodeDialog(project, "Optimize Imports", text, false);
        dialog.show();
        if (!dialog.isOK()) return;
        if (moduleContext != null) {
          new OptimizeImportsProcessor(project, moduleContext).run();
        }
        else {
          new OptimizeImportsProcessor(projectContext).run();
        }
        return;
      }

      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element == null) return;
      if (element instanceof PsiDirectory){
        dir = (PsiDirectory)element;
      }
      else{
        file = element.getContainingFile();
        if (file == null) return;
        dir = file.getContainingDirectory();
      }
    }

    final LayoutCodeDialog dialog = new LayoutCodeDialog(project, "Optimize Imports", file, dir, null, HELP_ID);
    dialog.show();
    if (!dialog.isOK()) return;

    String commandName = getTemplatePresentation().getText();
    if (dialog.isProcessDirectory()){
      new OptimizeImportsProcessor(project, dir, dialog.isIncludeSubdirectories()).run();
    }
    else{
      new OptimizeImportsProcessor(project, file).run();
    }
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
    if (editor != null){
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !(file instanceof PsiJavaFile)){
        presentation.setEnabled(false);
        return;
      }
    }
    else if (dataContext.getData(DataConstantsEx.MODULE_CONTEXT) == null &&
             dataContext.getData(DataConstantsEx.PROJECT_CONTEXT) == null) {
      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element == null){
        presentation.setEnabled(false);
        return;
      }

      if (!(element instanceof PsiDirectory)){
        PsiFile file = element.getContainingFile();
        if (file == null || !(file instanceof PsiJavaFile)){
          presentation.setEnabled(false);
          return;
        }
      }
      else{
        PsiPackage aPackage = ((PsiDirectory)element).getPackage();
        if (aPackage == null){
          presentation.setEnabled(false);
          return;
        }
      }
    }

    presentation.setEnabled(true);
  }
}
