package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;

/**
 * Filters block related nodes
 */
public class BlockFilter extends NodeFilter {
  @Override public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    result = true;
  }

  @Override public void visitCodeBlock(PsiCodeBlock psiCodeBlock) {
    result = true;
  }

  private BlockFilter() {
  }

  public static NodeFilter getInstance() {
    if (instance==null) instance = new BlockFilter();
    return instance;
  }
  private static NodeFilter instance;
}
