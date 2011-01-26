package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyClassFindUsagesHandler extends FindUsagesHandler {
  private final PyClass myClass;

  public PyClassFindUsagesHandler(@NotNull PyClass psiElement) {
    super(psiElement);
    myClass = psiElement;
  }

  @NotNull
  @Override
  public PsiElement[] getSecondaryElements() {
    final PyFunction initMethod = myClass.findMethodByName(PyNames.INIT, false);
    if (initMethod != null) {
      return new PsiElement[] { initMethod };
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected boolean isSearchForTextOccurencesAvailable(PsiElement psiElement, boolean isSingleFile) {
    return true;
  }
}
