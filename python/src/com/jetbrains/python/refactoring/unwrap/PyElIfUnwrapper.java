package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementWithElse;
import com.jetbrains.python.psi.impl.PyIfPartElifImpl;

import java.util.List;

/**
 * User : ktisha
 */
public class PyElIfUnwrapper extends PyUnwrapper {
  public PyElIfUnwrapper() {
    super(PyBundle.message("unwrap.elif"));
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context)
    throws IncorrectOperationException {
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PyStatementWithElse.class);
    context.extractPart(element);
    context.delete(parent);
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyStatementWithElse.class);
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
}
