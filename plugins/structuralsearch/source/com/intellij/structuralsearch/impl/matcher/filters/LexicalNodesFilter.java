package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.javadoc.PsiDocComment;

/**
 * Filter for lexical nodes
 */
final public class LexicalNodesFilter extends NodeFilter {
  private boolean careKeyWords;

  public void visitJavaToken(final PsiJavaToken t) {
    result = true;
  }

  public void visitComment(final PsiComment comment) {
  }

  public void visitDocComment(final PsiDocComment comment) {
  }

  public void visitKeyword(PsiKeyword keyword) {
    result = !careKeyWords;
  }

  public void visitWhiteSpace(final PsiWhiteSpace space) {
    result = true;
  }

  public void visitErrorElement(final PsiErrorElement element) {
    result = true;
  }

  public void visitXmlText(XmlText text) {
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
