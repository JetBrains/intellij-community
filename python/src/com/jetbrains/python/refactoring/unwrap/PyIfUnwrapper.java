package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyIfPartIfImpl;

import java.util.List;

/**
 * User : ktisha
 */
public class PyIfUnwrapper extends PyUnwrapper {
  public PyIfUnwrapper() {
    super(PyBundle.message("unwrap.if"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyIfPartIfImpl) {
      final PyStatementList statementList = ((PyIfPartIfImpl)e).getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyIfStatement.class);
  }


  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
    context.extractPart(ifStatement);
    context.delete(ifStatement);
  }
}
