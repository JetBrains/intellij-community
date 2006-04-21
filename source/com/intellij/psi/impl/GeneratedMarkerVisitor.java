package com.intellij.psi.impl;

import com.intellij.psi.impl.source.tree.RecursiveTreeElementVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;

public class GeneratedMarkerVisitor extends RecursiveTreeElementVisitor {
  protected boolean visitNode(TreeElement element) {
    CodeEditUtil.setNodeGenerated(element, true);
    return true;
  }
}
