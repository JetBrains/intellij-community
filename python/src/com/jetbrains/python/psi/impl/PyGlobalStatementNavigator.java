package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyGlobalStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyGlobalStatementNavigator {
  private PyGlobalStatementNavigator() {
  }

  @Nullable
  public static PyGlobalStatement getByArgument(final PsiElement element){
    final PsiElement parent = element.getParent();
    if (parent instanceof PyGlobalStatement){
      final PyGlobalStatement statement = (PyGlobalStatement)parent;
      return ArrayUtil.find(statement.getGlobals(), element) != -1 ? statement : null;
    }
    return null;
  }
}
