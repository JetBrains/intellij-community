package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass || isLocal(element);
  }

  private static boolean isLocal(PsiElement element) {
    if (!(element instanceof PyTargetExpression)) return false;
    return ((PyTargetExpressionImpl) element).getRedefinitionScope() != null;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof PyClass) {
      return new PyClassFindUsagesHandler((PyClass)element);
    }
    return new PyLocalFindUsagesHandler(element);
  }
}
