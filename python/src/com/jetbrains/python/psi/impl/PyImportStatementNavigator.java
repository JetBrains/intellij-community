package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyImportStatementNavigator {
  private PyImportStatementNavigator() {
  }

  @Nullable
  public static PyImportStatementBase getImportStatementByElement(final PsiElement element){
    final PyImportStatementBase statement = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class, false);
    if (statement == null) {
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
