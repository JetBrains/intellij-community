package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;

public abstract class BaseRefactoringAction extends AnAction {
  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(PsiElement[] elements);

  protected abstract RefactoringActionHandler getHandler(DataContext dataContext);

  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = (Editor) dataContext.getData(DataConstants.EDITOR);
    final PsiElement[] elements = getPsiElementArray(dataContext);
    RefactoringActionHandler handler = getHandler(dataContext);
    if (handler == null) return;
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      handler.invoke(project, editor, file, dataContext);
    } else {
      handler.invoke(project, elements, dataContext);
    }
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return false;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = (Editor) dataContext.getData(DataConstants.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !isAvaiableForFile(file)) {
        presentation.setEnabled(false);
      } else {
        presentation.setEnabled(true);
      }
    } else {
      if (isAvailableInEditorOnly()) {
        presentation.setEnabled(false);
        return;
      }
      final PsiElement[] elements = getPsiElementArray(dataContext);
      if (elements != null && elements.length != 0) {
        presentation.setEnabled(isEnabledOnElements(elements));
      } else if (isEnabledOnDataContext(dataContext)) {
        presentation.setEnabled(true);
        return;
      } else {
        presentation.setEnabled(false);
        return;
      }
    }
  }

  protected boolean isAvaiableForFile(PsiFile file) {
    return file.canContainJavaCode();
  }

  public static PsiElement[] getPsiElementArray(DataContext dataContext) {
    PsiElement[] psiElements = (PsiElement[]) dataContext.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
    if (psiElements == null || psiElements.length == 0) {
      PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element != null) {
        psiElements = new PsiElement[]{element};
      }
    }
    return psiElements;
  }

}