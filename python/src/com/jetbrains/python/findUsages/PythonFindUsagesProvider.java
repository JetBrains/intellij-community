package com.jetbrains.python.findUsages;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.findUsages.PyWordsScanner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement || psiElement instanceof PyReferenceExpression;
  }

  public String getHelpId(@NotNull PsiElement psiElement) {
    return null;
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    if (element instanceof PyNamedParameter) return "parameter";
    if (element instanceof PyFunction) return "function";
    if (element instanceof PyClass) return "class";
    if (element instanceof PyReferenceExpression || element instanceof PyTargetExpression) return "variable";
    return "";
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    if (element instanceof PyReferenceExpression) {
      String referencedName = ((PyReferenceExpression)element).getReferencedName();
      if (referencedName == null) {
        return "";
      }
      return referencedName;
    }
    return "";
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }

  public WordsScanner getWordsScanner() {
    return new PyWordsScanner();
  }
}
