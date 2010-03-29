package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.ComprhForComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyListCompExpressionNavigator {
  private PyListCompExpressionNavigator() {
  }

  @Nullable
  public static PyListCompExpression getPyListCompExpressionByVariable(final PsiElement element){
    final PyListCompExpression listCompExpression = PsiTreeUtil.getParentOfType(element, PyListCompExpression.class, false);
    if (listCompExpression == null){
      return null;
    }
    for (ComprhForComponent component : listCompExpression.getForComponents()) {
      final PyExpression variable = component.getIteratorVariable();
      if (variable != null && PsiTreeUtil.isAncestor(variable, element, false)){
        return listCompExpression;
      }
    }
    return null;
  }
}
