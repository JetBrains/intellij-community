package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyListCompExpressionNavigator;

/**
 * @author oleg
 */
public class UsageAnalyzer {
  public static boolean isParameter(final PsiElement element) {
    // Except Block
    if (PyExceptPartNavigator.getPyExceptPartByTarget(element) != null){
      return true;
    }

    // For iterable statement
    if (PyForStatementNavigator.getPyForStatementByIterable(element) != null){
      return true;
    }

    // List comprehension expression
   if (PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element) != null){
     return true;
   }

    if (element instanceof PyParameter) {
      return true;
    }
    return false;
  }
}
