package com.intellij.refactoring.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseRefactoringAction extends AnAction {
  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(PsiElement[] elements);

  protected boolean isAvailableOnElementInEditor(final PsiElement element) {
    return true;
  }

  @Nullable
  protected abstract RefactoringActionHandler getHandler(DataContext dataContext);

  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = e.getData(DataKeys.EDITOR);
    final PsiElement[] elements = getPsiElementArray(dataContext);
    RefactoringActionHandler handler = getHandler(dataContext);
    if (handler == null) return;
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file);
      handler.invoke(project, editor, file, dataContext);
    }
    else {
      handler.invoke(project, elements, dataContext);
    }
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return false;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(true);
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      disableAction(e);
      return;
    }

    Editor editor = e.getData(DataKeys.EDITOR);
    PsiFile file = e.getData(DataKeys.PSI_FILE);
    if (file != null) {
      if (file instanceof PsiCompiledElement || !isAvailableForFile(file)) {
        disableAction(e);
        return;
      }
    }

    if (editor != null) {
      PsiElement element = e.getData(DataKeys.PSI_ELEMENT);
      if (element == null || !isAvailableForLanguage(element.getLanguage())) {
        if (file == null) {
          disableAction(e);
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
      final boolean isEnabled = element != null && !isSyntheticJsp(element) && isAvailableForLanguage(element.getLanguage()) &&
        isAvailableOnElementInEditor(element);
      if (!isEnabled) {
        disableAction(e);
      }

    }
    else {
      if (isAvailableInEditorOnly()) {
        disableAction(e);
        return;
      }
      final PsiElement[] elements = getPsiElementArray(dataContext);
      final boolean isEnabled = isEnabledOnDataContext(dataContext) || (elements.length != 0 && isEnabledOnElements(elements));
      if (!isEnabled) {
        disableAction(e);
      }
    }
  }

  private static void disableAction(final AnActionEvent e) {
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setEnabled(false);
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

  @NotNull
  public static PsiElement[] getPsiElementArray(DataContext dataContext) {
    PsiElement[] psiElements = DataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements == null || psiElements.length == 0) {
      PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
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