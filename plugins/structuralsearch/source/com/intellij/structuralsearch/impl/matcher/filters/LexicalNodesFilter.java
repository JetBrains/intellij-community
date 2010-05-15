package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Filter for lexical nodes
 */
public final class LexicalNodesFilter  implements NodeFilter {
  private boolean careKeyWords;
  private boolean result;

  private final PsiElementVisitor myJavaVisitor = new JavaLexicalNodesFilter(this);
  private final PsiElementVisitor myXmlVistVisitor = new XmlLexicalNodesFilter(this);

  private LexicalNodesFilter() {
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  public boolean getResult() {
    return result;
  }

  public void setResult(boolean result) {
    this.result = result;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new LexicalNodesFilter();

    private NodeFilterHolder() {
    }
  }

  public boolean isCareKeyWords() {
    return careKeyWords;
  }

  public void setCareKeyWords(boolean careKeyWords) {
    this.careKeyWords = careKeyWords;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) {
      element.accept(myJavaVisitor);
      element.accept(myXmlVistVisitor);
    }
    return result;
  }

}
