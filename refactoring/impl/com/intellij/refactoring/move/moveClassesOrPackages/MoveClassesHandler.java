package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import org.jetbrains.annotations.Nullable;

public class MoveClassesHandler extends MoveClassesOrPackagesHandlerBase {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (element instanceof JspClass) return false;
      if (!(element instanceof PsiClass)) return false;
      if (!(element.getParent() instanceof PsiFile)) return false;
    }
    return targetContainer == null || targetContainer instanceof PsiClass ||
           MovePackagesHandler.isPackageOrDirectory(targetContainer);
  }
}
