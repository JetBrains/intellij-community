package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class MoveInstanceMethodHandlerDelegate extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    if (!(element instanceof PsiMethod)) return false;
    if (element instanceof JspHolderMethod) return false;
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return targetContainer == null ||
           (targetContainer instanceof PsiClass && !(targetContainer instanceof PsiAnonymousClass));
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    new MoveInstanceMethodHandler().invoke(project, elements, null);
  }
}
