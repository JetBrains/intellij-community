
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;

public class AnonymousToInnerAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element, final Editor editor) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(element.getProject()).getPsiFile(document);
    if (file != null) {
      final PsiElement targetElement = file.findElementAt(editor.getCaretModel().getOffset());
      if (PsiTreeUtil.getParentOfType(targetElement, PsiAnonymousClass.class) != null) {
        return true;
      }
    }
    if (PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class) != null) {
      return true;
    }
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    return newExpression != null && newExpression.getAnonymousClass() != null;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new AnonymousToInnerHandler();
  }
}