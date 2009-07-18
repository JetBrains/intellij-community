package com.intellij.psi.impl.file;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Maxim.Mossienko
 *         Date: Sep 18, 2008
 *         Time: 3:33:07 PM
 */
public class JavaUpdateAddedFileProcessor extends UpdateAddedFileProcessor {
  public boolean canProcessElement(final PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  public void update(final PsiFile element, PsiFile originalElement) throws IncorrectOperationException {
    if (PsiUtilBase.getTemplateLanguageFile(element) != element.getContainingFile()) return;

    PsiDirectory dir = element.getContainingDirectory();
    if (dir == null) return;
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
    if (aPackage == null) return;
    String packageName = aPackage.getQualifiedName();

    ((PsiClassOwner)element).setPackageName(packageName);
  }
}
