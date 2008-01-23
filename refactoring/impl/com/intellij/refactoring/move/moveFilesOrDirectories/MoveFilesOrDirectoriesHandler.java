package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class MoveFilesOrDirectoriesHandler extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler");

  public boolean canMove(final PsiElement[] elements, final PsiElement targetContainer) {
    if (!canMoveFiles(elements) && !canMoveDirectories(elements)) return false;
    return targetContainer == null || targetContainer instanceof PsiDirectory || targetContainer instanceof PsiPackage;
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (!LOG.assertTrue(targetContainer == null || targetContainer instanceof PsiDirectory || targetContainer instanceof PsiPackage )) {
      return;
    }
    MoveFilesOrDirectoriesUtil.doMove(project, elements, targetContainer, callback);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference) {
    if ((element instanceof PsiFile && ((PsiFile)element).getVirtualFile() != null)
        || element instanceof PsiDirectory) {
      final PsiElement targetContainer = (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
      MoveFilesOrDirectoriesUtil.doMove(project, new PsiElement[]{element}, targetContainer, null);
      return true;
    }
    return false;
  }

  public static boolean canMoveFiles(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) || (element instanceof PsiJavaFile && !(PsiUtil.isInJspFile(element)))) {
        return false;
      }
    }

    // the second 'for' statement is for effectivity - to prevent creation of the 'names' array
    HashSet<String> names = new HashSet<String>();
    for (PsiElement element : elements) {
      PsiFile file = (PsiFile)element;
      String name = file.getName();
      if (names.contains(name)) {
        return false;
      }

      names.add(name);
    }

    return true;
  }

  public static boolean canMoveDirectories(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    for (PsiElement element : elements) {
      PsiDirectory directory = (PsiDirectory)element;

      if (hasPackages(directory)) {
        return false;
      }
    }

    PsiElement[] filteredElements = PsiTreeUtil.filterAncestors(elements);
    if (filteredElements.length != elements.length) {
      // there are nested dirs
      return false;
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
