package com.jetbrains.python.edu;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStatement;

public class PyEduUtils {
  public static boolean isFirstCodeLine(PsiElement element) {
    return element instanceof PyStatement &&
           element.getParent() instanceof PyFile &&
           !isNothing(element) &&
           nothingBefore(element);
  }

  private static boolean nothingBefore(PsiElement element) {
    element = element.getPrevSibling();
    while (element != null) {
      if (!isNothing(element)) {
        return false;
      }
      element = element.getPrevSibling();
    }

    return true;
  }

  private static boolean isNothing(PsiElement element) {
    return (element instanceof PsiComment) || (element instanceof PyImportStatement) || (element instanceof PsiWhiteSpace);
  }
}
