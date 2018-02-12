package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyTargetExpressionFindUsagesHandler extends PyFindUsagesHandler {
  public PyTargetExpressionFindUsagesHandler(@NotNull PyTargetExpression psiElement) {
    super(psiElement);
  }
}
