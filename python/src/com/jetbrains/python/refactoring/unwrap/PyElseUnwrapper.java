package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementWithElse;

import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public class PyElseUnwrapper extends PyElseUnwrapperBase {
  public PyElseUnwrapper() {
    super(PyBundle.message("unwrap.else"));
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyStatementWithElse.class);
  }

  @Override
  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
    PsiElement parent = element.getParent();

    while (parent instanceof PyIfStatement) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  @Override
  protected void unwrapElseBranch(PyElement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    parent = PsiTreeUtil.getParentOfType(branch, PyStatementWithElse.class);
    context.extractPart(branch);
    context.delete(parent);
  }
}
