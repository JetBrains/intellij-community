package com.jetbrains.python.refactoring.unwrap;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;

/**
 * User : ktisha
 */
public class PyWithUnwrapper extends PyUnwrapper {
  public PyWithUnwrapper() {
    super(PyBundle.message("unwrap.with"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyWithStatement) {
      ASTNode n = e.getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
      if (n != null) {
        final PyStatementList statementList = (PyStatementList)n.getPsi();
        if (statementList != null) {
          final PyStatement[] statements = statementList.getStatements();
          return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
        }
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyWithStatement withStatement = (PyWithStatement)element;
    context.extractPart(withStatement);
    context.delete(withStatement);
  }
}

