package com.intellij.refactoring.move.moveFilesOrDirectories;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.copy.JavaCopyFilesOrDirectoriesHandler;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  protected boolean canMoveFiles(@NotNull final PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) ||
          element instanceof PsiJavaFile && !PsiUtil.isInJspFile(element)) {
        return false;
      }
    }
    return super.canMoveFiles(elements);
  }

  protected boolean canMoveDirectories(@NotNull final PsiElement[] elements) {
    if (!super.canMoveDirectories(elements)) return false;

    for (PsiElement element1 : elements) {
      PsiDirectory directory = (PsiDirectory)element1;

      if (JavaCopyFilesOrDirectoriesHandler.hasPackages(directory)) {
        return false;
      }
    }
    return true;
  }
}
