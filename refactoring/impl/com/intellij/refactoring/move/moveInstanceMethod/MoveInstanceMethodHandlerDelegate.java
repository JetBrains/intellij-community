package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.Nullable;

public class MoveInstanceMethodHandlerDelegate extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    if (!(element instanceof PsiMethod)) return false;
    if (element instanceof JspHolderMethod) return false;
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.hasModifierProperty(PsiModifier.STATIC))  {
        new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
        return true;
      }
    }
    return false;
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    new MoveInstanceMethodHandler().invoke(project, elements, null);
  }
}
