package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class MoveInnerToUpperHandler extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
           !((PsiClass) element).hasModifierProperty(PsiModifier.STATIC) &&
           (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false)));
                                                                        
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    MoveInnerImpl.doMove(project, elements, callback);
  }
}
