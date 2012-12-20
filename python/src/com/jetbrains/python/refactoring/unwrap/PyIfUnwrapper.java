package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;

/**
 * User : ktisha
 */
public class PyIfUnwrapper extends PyUnwrapper {
  public PyIfUnwrapper() {
    super(PyBundle.message("unwrap.if"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyIfStatement) {
      final PyStatementList statementList = ((PyIfStatement)e).getIfPart().getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyIfStatement ifStatement = (PyIfStatement)element;
    context.extractPart(ifStatement);
    context.delete(ifStatement);
  }
}
