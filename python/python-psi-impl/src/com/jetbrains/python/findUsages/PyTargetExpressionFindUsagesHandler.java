package com.jetbrains.python.findUsages;

import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public class PyTargetExpressionFindUsagesHandler extends PyFindUsagesHandler {
  public PyTargetExpressionFindUsagesHandler(@NotNull PyTargetExpression psiElement) {
    super(psiElement);
  }
}
