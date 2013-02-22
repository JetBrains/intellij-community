package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;

/**
 * User : ktisha
 */
public class PyForUnwrapper extends PyUnwrapper {
  public PyForUnwrapper() {
    super(PyBundle.message("unwrap.for"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyForStatement) {
      final PyStatementList statementList = ((PyForStatement)e).getForPart().getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyForStatement forStatement = (PyForStatement)element;
    context.extractPart(forStatement);
    context.extractPart(forStatement.getElsePart());
    context.delete(forStatement);
  }
}

