package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;

/**
 * Filter for lexical nodes
 */
public final class LexicalNodesFilter  implements NodeFilter {
  private boolean careKeyWords;
  private boolean result;

  private LexicalNodesFilter() {}

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
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
      if (profile != null) {
        element.accept(profile.getLexicalNodesFilter(this));
      }
    }
    return result;
  }
}
