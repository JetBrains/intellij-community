package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;

/**
 * User : ktisha
 */
public class PyWhileUnwrapper extends PyUnwrapper {
  public PyWhileUnwrapper() {
    super(PyBundle.message("unwrap.while"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyWhileStatement) {
      final PyStatementList statementList = ((PyWhileStatement)e).getWhilePart().getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyWhileStatement whileStatement = (PyWhileStatement)element;
    context.extractPart(whileStatement);
    context.extractPart(whileStatement.getElsePart());
    context.delete(whileStatement);
  }
}

