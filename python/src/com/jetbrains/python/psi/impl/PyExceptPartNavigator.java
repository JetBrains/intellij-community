package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyExceptPartNavigator {
  private PyExceptPartNavigator() {
  }

  @Nullable
  public static PyExceptPart getPyExceptPartByTarget(final PsiElement element){
    final PyExceptPart pyExceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart.class, false);
    if (pyExceptPart == null){
      return null;
    }
    final PyExpression expr = pyExceptPart.getTarget();
    if (expr != null && PsiTreeUtil.isAncestor(expr, element, false)){
      return pyExceptPart;
    }
    return null;
  }
}
