package com.intellij.refactoring.copy;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * @author yole
 */
public class JavaCopyFilesOrDirectoriesHandler extends CopyFilesOrDirectoriesHandler {
  protected boolean canCopyFiles(final PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) ||
          element instanceof PsiJavaFile && !PsiUtil.isInJspFile(element)) {
        return false;
      }
    }

    return super.canCopyFiles(elements);
  }

  protected boolean canCopyDirectories(final PsiElement[] elements) {
    if (!super.canCopyDirectories(elements)) return false;

    for (PsiElement element1 : elements) {
      PsiDirectory directory = (PsiDirectory)element1;

      if (hasPackages(directory)) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasPackages(PsiDirectory directory) {
    if (JavaDirectoryService.getInstance().getPackage(directory) != null) {
      return true;
    }
    PsiDirectory[] subdirectories = directory.getSubdirectories();
    for (PsiDirectory subdirectory : subdirectories) {
      if (hasPackages(subdirectory)) {
        return true;
      }
    }
    return false;
  }
}
