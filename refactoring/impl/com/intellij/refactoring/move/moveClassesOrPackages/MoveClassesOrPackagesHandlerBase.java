package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.openapi.project.Project;

import java.util.Arrays;

public abstract class MoveClassesOrPackagesHandlerBase extends MoveHandlerDelegate {
  protected static boolean isPackageOrDirectory(final PsiElement element) {
    if (element instanceof PsiPackage) return true;
    if (element instanceof PsiDirectory) {
      return JavaDirectoryService.getInstance().getPackage((PsiDirectory) element) != null;
    }
    return false;
  }

  public PsiElement[] adjustForMove(final Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    return MoveClassesOrPackagesImpl.adjustForMove(project,sourceElements, targetElement);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (tryDirectoryMove ( project, elements, targetContainer, callback)) {
      return;
    }
    MoveClassesOrPackagesImpl.doMove(project, elements, targetContainer, callback);
  }

  private static boolean tryDirectoryMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement, final MoveCallback callback) {
    if (targetElement instanceof PsiDirectory) {
      final PsiElement[] adjustedElements = MoveClassesOrPackagesImpl.adjustForMove(project, sourceElements, targetElement);
      if (adjustedElements != null) {
        if ( CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements),true) ) {
          new MoveClassesOrPackagesToNewDirectoryDialog((PsiDirectory)targetElement, adjustedElements, callback).show();
        }
      }
      return true;
    }
    return false;
  }

}
