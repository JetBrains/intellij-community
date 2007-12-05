package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 17:46:10
 * To change this template use Options | File Templates.
 */
public class StatementFilter extends NodeFilter {
  @Override public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
    result = false;
  }

  @Override public void visitStatement(PsiStatement psiStatement) {
    result = true;
  }

  @Override public void visitComment(PsiComment comment) {
    result = true;
  }
}
