package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyImportStatementNavigator {
  @Nullable
  public static PyImportStatement getImportStatementByElement(final PsiElement element){
    final PyImportStatement statement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class, false);
    if (statement == null){
      return null;
    }
    for (PyImportElement importElement : statement.getImportElements()) {
      if (element == importElement || element == importElement.getImportReference()){
        return statement;
      }
    }
    return null;
  }
}
