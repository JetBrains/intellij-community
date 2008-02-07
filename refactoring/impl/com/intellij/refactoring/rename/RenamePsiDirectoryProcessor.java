package com.intellij.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

/**
 * @author yole
 */
public class RenamePsiDirectoryProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiDirectory;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiDirectory aDirectory = (PsiDirectory) element;
    // rename all non-package statement references
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) != null) continue;
      RenameUtil.rename(usage, newName);
    }

    //rename package statement
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) == null) continue;
      RenameUtil.rename(usage, newName);
    }

    aDirectory.setName(newName);
    listener.elementRenamed(aDirectory);
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    if (psiPackage != null) {
      return RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, nonJava);
    }
    return newName;
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) element);
    if (aPackage != null && aPackage.getName() != null) {
      allRenames.put(aPackage, newName);
      RenamePsiPackageProcessor.preparePackageRenaming(aPackage, newName, allRenames);
    }
  }
}
