package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiComment;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 17:46:10
 * To change this template use Options | File Templates.
 */
public class StatementFilter extends NodeFilter {
  public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = false;
  }

  public void visitStatement(PsiStatement psiStatement) {
    result = true;
  }

  public void visitComment(PsiComment comment) {
    result = true;
  }
}
