package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;

/**
* @author Eugene.Kudelevsky
*/
public class JavaLexicalNodesFilter extends JavaElementVisitor {
  private final LexicalNodesFilter myLexicalNodesFilter;

  public JavaLexicalNodesFilter(LexicalNodesFilter lexicalNodesFilter) {
    this.myLexicalNodesFilter = lexicalNodesFilter;
  }

  @Override public void visitJavaToken(final PsiJavaToken t) {
    myLexicalNodesFilter.setResult(true);
  }

  @Override public void visitComment(final PsiComment comment) {
  }

  @Override public void visitDocComment(final PsiDocComment comment) {
  }

  @Override public void visitKeyword(PsiKeyword keyword) {
    myLexicalNodesFilter.setResult(!myLexicalNodesFilter.isCareKeyWords());
  }

  @Override public void visitWhiteSpace(final PsiWhiteSpace space) {
    myLexicalNodesFilter.setResult(true);
  }

  @Override public void visitErrorElement(final PsiErrorElement element) {
    myLexicalNodesFilter.setResult(true);
  }
}
