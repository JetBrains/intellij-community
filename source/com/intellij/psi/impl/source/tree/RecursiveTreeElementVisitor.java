package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.parsing.ChameleonTransforming;

public abstract class RecursiveTreeElementVisitor extends TreeElementVisitor{
  public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  public void visitComposite(CompositeElement composite) {
    if(!visitNode(composite)) return;
    ChameleonTransforming.transformChildren(composite);
    TreeElement child = composite.getFirstChildNode();
    while(child != null) {
      child.acceptTree(this);
      child = child.getTreeNext();
    }
  }

  protected abstract boolean visitNode(TreeElement element);
}
