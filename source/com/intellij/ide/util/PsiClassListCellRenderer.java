package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

public class PsiClassListCellRenderer extends PsiElementListCellRenderer<PsiClass> {
  public String getElementText(PsiClass element) {
    return ClassPresentationUtil.getNameForClass(element, false);
  }

  protected String getContainerText(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      String packageName = javaFile.getPackageName();
      if (packageName.length() == 0) return null;
      return "(" + packageName + ")";
    }
    return null;
  }

  protected int getIconFlags() {
    return 0;
  }
}
