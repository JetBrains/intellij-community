package com.jetbrains.python.edu;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.Predicate;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.Nullable;

public class PyEduUtils {

  public static boolean isFirstCodeLine(PsiElement element) {
    return isFirstCodeLine(element, DEFAULT_CONDITION);
  }

  public static boolean isFirstCodeLine(PsiElement element, Predicate<PsiElement> isNothing) {
    return element instanceof PyStatement &&
           element.getParent() instanceof PyFile &&
           !isNothing.apply(element) &&
           nothingBefore(element, isNothing);
  }

  private static boolean nothingBefore(PsiElement element, Predicate<PsiElement> isNothing) {
    element = element.getPrevSibling();
    while (element != null) {
      if (!isNothing.apply(element)) {
        return false;
      }
      element = element.getPrevSibling();
    }

    return true;
  }

  private static Predicate<PsiElement> DEFAULT_CONDITION = new Predicate<PsiElement>() {
    @Override
    public boolean apply(@Nullable PsiElement element) {
      return (element instanceof PsiComment) || (element instanceof PyImportStatement) || (element instanceof PsiWhiteSpace);
    }
  };
}
