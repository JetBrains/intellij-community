package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.parsing.ChameleonTransforming;

public abstract class RecursiveTreeElementVisitor extends TreeElementVisitor{
  public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  public void visitComposite(CompositeElement composite) {
    ChameleonTransforming.transformChildren(composite);
    if(!visitNode(composite)) return;
    TreeElement child = composite.getFirstChildNode();
    while(child != null) {
      final TreeElement treeNext = child.getTreeNext();
      child.acceptTree(this);
      child = treeNext;
    }
  }

  protected abstract boolean visitNode(TreeElement element);
}
