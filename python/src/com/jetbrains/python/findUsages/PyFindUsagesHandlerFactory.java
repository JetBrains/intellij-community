package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new PyClassFindUsagesHandler((PyClass)element);
  }
}
