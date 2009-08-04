package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.copy.JavaCopyFilesOrDirectoriesHandler;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory && JavaCopyFilesOrDirectoriesHandler.hasPackages((PsiDirectory)element)) return false; //can't move packages && dirs at the same time
      if (element instanceof PsiJavaFile && !PsiUtil.isInJspFile(element)) {
        return false;
      }
    }
    return super.canMove(elements, targetContainer);
  }

}
