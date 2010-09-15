package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyForStatementNavigator {
  private PyForStatementNavigator() {
  }

  @Nullable
  public static PyForStatement getPyForStatementByIterable(final PsiElement element){
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null){
      return null;
    }
    final PyExpression target = forStatement.getForPart().getTarget();
    if (target != null && PsiTreeUtil.isAncestor(target, element, false)){
      return forStatement;
    }
    return null;
  }

  @Nullable
  public static Object getPyForStatementByBody(final PsiElement element) {
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null){
      return null;
    }
    final PyForPart forPart = forStatement.getForPart();
    return forPart == element || forPart.getStatementList() == element ? forStatement : null;
  }
}
