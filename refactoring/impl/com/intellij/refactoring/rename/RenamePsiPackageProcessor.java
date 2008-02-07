package com.intellij.refactoring.rename;

import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

/**
 * @author yole
 */
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiPackage;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiPackage psiPackage = (PsiPackage)element;
    psiPackage.handleQualifiedNameChange(RenameUtil.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return getPackageQualifiedNameAfterRename((PsiPackage)element, newName, nonJava);
  }

  public static String getPackageQualifiedNameAfterRename(final PsiPackage element, final String newName, final boolean nonJava) {
    if (nonJava) {
      String qName = element.getQualifiedName();
      int index = qName.lastIndexOf('.');
      return index < 0 ? newName : qName.substring(0, index + 1) + newName;
    }
    else {
      return newName;
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    preparePackageRenaming((PsiPackage)element, newName, allRenames);
  }

  public static void preparePackageRenaming(PsiPackage psiPackage, final String newName, Map<PsiElement, String> allRenames) {
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        allRenames.put(directory, newName);
      }
    }
  }
}
