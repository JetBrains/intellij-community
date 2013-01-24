package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.impl.PyIfPartElifImpl;

/**
 * User : ktisha
 */
public class PyElIfRemover extends PyUnwrapper {
  public PyElIfRemover() {
    super(PyBundle.message("remove.elif"));
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyIfPartElifImpl) {
      final PyStatementList statementList = ((PyIfPartElifImpl)e).getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    context.delete(element);
  }
}
