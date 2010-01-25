package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyListCompExpressionNavigator;

/**
 * @author oleg
 */
public class ScopeUtil {
  public static PsiElement getScopeElement(final PsiElement element) {
    if (element instanceof PyNamedParameter){
      final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
      if (function != null){
        return function;
      }
    }

    final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
    if (exceptPart != null){
      return exceptPart;
    }

    final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
    if (forStatement != null){
      return forStatement;
    }

    final PyListCompExpression listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element);
    if (listCompExpression != null){
      return listCompExpression;
    }

    final ControlFlowOwner owner = PsiTreeUtil.getParentOfType(element, ControlFlowOwner.class, false);
    assert owner != null : "element should have not null controlflow owner";
    return owner;
  }
}
