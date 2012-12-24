package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;

/**
 * User : ktisha
 */
public class PyTryUnwrapper extends PyUnwrapper {
  public PyTryUnwrapper() {
    super(PyBundle.message("unwrap.while"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PyTryExceptStatement;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyTryExceptStatement statement = (PyTryExceptStatement)element;
    context.extractPart(statement);
    context.delete(statement);
  }
}

