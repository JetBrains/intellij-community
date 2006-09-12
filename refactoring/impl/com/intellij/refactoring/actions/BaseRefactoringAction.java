package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class BaseRefactoringAction extends AnAction {
  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(PsiElement[] elements);

  protected abstract @Nullable RefactoringActionHandler getHandler(DataContext dataContext);

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
    PsiFile file = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if (file != null) {
      if (file instanceof PsiCompiledElement || !isAvailableForFile(file)) {
        presentation.setEnabled(false);
        return;
      }
    }

    if (editor != null) {
      PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element == null || !isAvailableForLanguage(element.getLanguage())) {
        if (file == null) {
          presentation.setEnabled(false);
          return;
        }
        final int offset = editor.getCaretModel().getOffset();
        element = file.findElementAt(offset);
        if (element == null && offset == file.getTextLength()) {
          element = file.findElementAt(offset - 1);
        }

        if (element instanceof PsiWhiteSpace) {
          element = file.findElementAt(element.getTextRange().getStartOffset() - 1);
        }
      }
      presentation.setEnabled(element != null && !isSyntheticJsp(element) && isAvailableForLanguage(element.getLanguage()));

    } else {
      if (isAvailableInEditorOnly()) {
        presentation.setEnabled(false);
        return;
      }
      final PsiElement[] elements = getPsiElementArray(dataContext);
      if (isEnabledOnDataContext(dataContext)) {
        presentation.setEnabled(true);
      }
      else if (elements != null && elements.length != 0) {
        presentation.setEnabled(isEnabledOnElements(elements));
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }

  private static boolean isSyntheticJsp(final PsiElement element) {
    return element instanceof JspHolderMethod || element instanceof JspClass;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language.equals(StdFileTypes.JAVA.getLanguage());
  }

  protected boolean isAvailableForFile(PsiFile file) {
    return true;
  }

  public static PsiElement[] getPsiElementArray(DataContext dataContext) {
    PsiElement[] psiElements = (PsiElement[]) dataContext.getData(DataConstants.PSI_ELEMENT_ARRAY);
    if (psiElements == null || psiElements.length == 0) {
      PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
      if (element != null) {
        psiElements = new PsiElement[]{element};
      }
    }

    if (psiElements == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> filtered = null;
    for (PsiElement element : psiElements) {
      if (isSyntheticJsp(element)) {
        if (filtered == null) filtered = new ArrayList<PsiElement>(Arrays.asList(element));
        filtered.remove(element);
      }
    }
    return filtered == null ? psiElements : filtered.toArray(new PsiElement[filtered.size()]);
  }

}