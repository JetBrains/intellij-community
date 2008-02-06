package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.Pair;

import java.util.Collection;

/**
 * @author yole
 */
public class RenamePsiPackageProcessor implements RenamePsiElementProcessor {
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

  public Collection<PsiReference> findReferences(final PsiElement element) {
    return null;
  }

  public Pair<String, String> getTextOccurrenceSearchStrings(final PsiElement element, final String newName) {
    return null;
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
}
