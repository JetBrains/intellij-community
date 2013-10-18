package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElement;

/**
 * User : ktisha
 */
public class PyElseRemover extends PyElseUnwrapperBase {
  public PyElseRemover() {
    super(PyBundle.message("remove.else"));
  }

  @Override
  protected void unwrapElseBranch(PyElement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    context.delete(branch);
  }
}
