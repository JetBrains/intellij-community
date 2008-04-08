package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

public class MoveClassesHandler extends MoveClassesOrPackagesHandlerBase {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (element instanceof JspClass) return false;
      if (!(element instanceof PsiClass)) return false;
      if (!(element.getParent() instanceof PsiFile)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return psiElement instanceof PsiClass ||
           MovePackagesHandler.isPackageOrDirectory(psiElement);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isReferenceInAnonymousClass(reference)) return false;

    if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass) && element.getParent() instanceof PsiFile) {
      MoveClassesOrPackagesImpl.doMove(project, new PsiElement[]{element},
                                       (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT), null);
      return true;
    }
    return false;
  }

  public static boolean isReferenceInAnonymousClass(@Nullable final PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement &&
       ((PsiJavaCodeReferenceElement)reference).getParent() instanceof PsiAnonymousClass) {
      return true;
    }
    return false;
  }
}
