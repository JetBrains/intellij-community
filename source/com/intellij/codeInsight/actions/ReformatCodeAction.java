package com.intellij.codeInsight.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;

public class ReformatCodeAction extends AnAction {
  private static final String HELP_ID = "editing.codeReformatting";

  public void actionPerformed(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);

    PsiFile file = null;
    final PsiDirectory dir;
    boolean hasSelection = false;

    if (editor != null){
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      dir = file.getContainingDirectory();
      hasSelection = editor.getSelectionModel().hasSelection();
    }
    else{
      Project projectContext = (Project)dataContext.getData(DataConstantsEx.PROJECT_CONTEXT);
      Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);

      if (projectContext != null || moduleContext != null) {
        final String text;
        if (moduleContext != null) {
          text = "Module '" + moduleContext.getModuleFilePath() + "'";
        }
        else {
          text = "Project '" + projectContext.getProjectFilePath() + "'";
        }

        LayoutProjectCodeDialog dialog = new LayoutProjectCodeDialog(project, "Reformat Code", text, true);
        dialog.show();
        if (!dialog.isOK()) return;
        if (dialog.isOptimizeImports()) {
          if (moduleContext != null) {
            new ReformatAndOptimizeImportsProcessor(project, moduleContext).run();
          }
          else {
            new ReformatAndOptimizeImportsProcessor(projectContext).run();
          }
        }
        else {
          if (moduleContext != null) {
            new ReformatCodeProcessor(project, moduleContext).run();
          }
          else {
            new ReformatCodeProcessor(projectContext).run();
          }
        }
        return;
      }

      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element == null) return;
      if (element instanceof PsiDirectory) {
        dir = (PsiDirectory)element;
      }
      else {
        file = element.getContainingFile();
        if (file == null) return;
        dir = file.getContainingDirectory();
      }
    }

    final LayoutCodeDialog dialog = new LayoutCodeDialog(project, "Reformat Code", file, dir, hasSelection ? Boolean.TRUE : Boolean.FALSE, HELP_ID);
    dialog.show();
    if (!dialog.isOK()) return;

    final boolean optimizeImports = dialog.isOptimizeImports();
    if (dialog.isProcessDirectory()){
      if (optimizeImports) {
        new ReformatAndOptimizeImportsProcessor(project, dir, dialog.isIncludeSubdirectories()).run();
      }
      else {
        new ReformatCodeProcessor(project, dir, dialog.isIncludeSubdirectories()).run();
      }
    }
    else{
      final TextRange range;
      if (editor != null && dialog.isProcessSelectedText()){
        range = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
      }
      else{
        range = null;
      }

      if (optimizeImports && range == null) {
        new ReformatAndOptimizeImportsProcessor(project, file).run();
      }
      else {
        new ReformatCodeProcessor(project, file, range).run();
      }
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
      if (file == null || (!(file instanceof PsiJavaFile) && !(file instanceof XmlFile))){
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
        if (file == null || (!(file instanceof PsiJavaFile) && !(file instanceof XmlFile))){
          presentation.setEnabled(false);
          return;
        }
      }
    }
    presentation.setEnabled(true);
  }
}
