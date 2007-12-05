package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlText;

/**
 * Filter for lexical nodes
 */
final public class LexicalNodesFilter extends NodeFilter {
  private boolean careKeyWords;

  @Override public void visitJavaToken(final PsiJavaToken t) {
    result = true;
  }

  @Override public void visitComment(final PsiComment comment) {
  }

  @Override public void visitDocComment(final PsiDocComment comment) {
  }

  @Override public void visitKeyword(PsiKeyword keyword) {
    result = !careKeyWords;
  }

  @Override public void visitWhiteSpace(final PsiWhiteSpace space) {
    result = true;
  }

  @Override public void visitErrorElement(final PsiErrorElement element) {
    result = true;
  }

  @Override public void visitXmlText(XmlText text) {
    result = true;
  }

  private LexicalNodesFilter() {}

  public static NodeFilter getInstance() {
    if (instance==null) instance = new LexicalNodesFilter();
    return instance;
  }
  private static NodeFilter instance;

  public boolean isCareKeyWords() {
    return careKeyWords;
  }

  public void setCareKeyWords(boolean careKeyWords) {
    this.careKeyWords = careKeyWords;
  }
}
