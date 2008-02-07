package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;

public class JavaVetoRenameCondition implements Condition<PsiElement> {
  public boolean value(final PsiElement element) {
    return element instanceof PsiJavaFile && !PsiUtil.isInJspFile(element);
  }
}
