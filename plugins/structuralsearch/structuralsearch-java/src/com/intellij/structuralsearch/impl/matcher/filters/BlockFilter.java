package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;

/**
 * Filters block related nodes
 */
public class BlockFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }

  @Override
  public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    result = true;
  }

  @Override
  public void visitCodeBlock(PsiCodeBlock psiCodeBlock) {
    result = true;
  }

  private BlockFilter() {
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new BlockFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

}
