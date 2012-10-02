package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyFunctionFindUsagesHandler extends FindUsagesHandler {
  private final List<PsiElement> myAllElements;

  protected PyFunctionFindUsagesHandler(@NotNull PsiElement psiElement) {
    super(psiElement);
    myAllElements = null;
  }

  protected PyFunctionFindUsagesHandler(@NotNull PsiElement psiElement, List<PsiElement> allElements) {
    super(psiElement);
    myAllElements = allElements;
  }

  @Override
  protected boolean isSearchForTextOccurencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return true;
  }

  @NotNull
  @Override
  public PsiElement[] getPrimaryElements() {
    return myAllElements != null ? myAllElements.toArray(new PsiElement[myAllElements.size()]) : super.getPrimaryElements();
  }
}
