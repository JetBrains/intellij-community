package com.intellij.refactoring.move;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class MoveHandlerDelegate {
  public static final ExtensionPointName<MoveHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.moveHandler");

  public abstract boolean canMove(PsiElement[] elements, @Nullable final PsiElement targetContainer);

  public abstract void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback);

  @Nullable
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return sourceElements;
  }
}
