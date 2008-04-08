package com.intellij.refactoring.anonymousToInner;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.MoveHandlerDelegate;

/**
 * @author yole
 */
public class MoveAnonymousToInnerHandler extends MoveHandlerDelegate {
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiAnonymousClass) {
      new AnonymousToInnerHandler().invoke(project, (PsiAnonymousClass)element);
      return true;
    }
    return false;
  }
}
