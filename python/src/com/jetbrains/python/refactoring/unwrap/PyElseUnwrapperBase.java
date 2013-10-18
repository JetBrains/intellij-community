package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyIfStatement;

/**
 * User : ktisha
 */
public abstract class PyElseUnwrapperBase extends PyUnwrapper {
  public PyElseUnwrapperBase(String description) {
    super(description);
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
      return (e instanceof PyElsePart);
  }
  @Override
    protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
      PyElement elseBranch;

      if (element instanceof PyIfStatement && ((PyIfStatement)element).getElsePart() != null) {
        elseBranch = ((PyIfStatement)element).getElsePart();
      }
      else {
        elseBranch = (PyElement)element;
      }
      unwrapElseBranch(elseBranch, element.getParent(), context);
    }
  protected abstract void unwrapElseBranch(PyElement branch, PsiElement parent, Context context) throws IncorrectOperationException;

}
