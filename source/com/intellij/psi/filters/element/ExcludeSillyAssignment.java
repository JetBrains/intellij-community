package com.intellij.psi.filters.element;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 17:59:58
 * To change this template use Options | File Templates.
 */
public class ExcludeSillyAssignment implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if(!(element instanceof PsiElement)) return true;

    final PsiAssignmentExpression expression = PsiTreeUtil.getParentOfType(context, PsiAssignmentExpression.class, false, PsiClass.class);
    if (expression == null) return true;

    final PsiExpression left = expression.getLExpression();
    return !(left instanceof PsiReference) || !((PsiReference)left).isReferenceTo((PsiElement)element);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
