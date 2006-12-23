package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.util.diff.DiffTreeStructure;

import java.util.List;

/**
 * @author max
 */
public class ASTDiffTreeStructure implements DiffTreeStructure<ASTNode> {
  private ASTNode myRoot;

  public ASTDiffTreeStructure(final ASTNode root) {
    ChameleonTransforming.transformChildren(root);
    myRoot = root;
  }

  public ASTNode prepareForGetChildren(final ASTNode astNode) {
    if (astNode instanceof ChameleonElement) {
      return ChameleonTransforming.transform((LeafElement)astNode);
    }
    return astNode;
  }

  public ASTNode getRoot() {
    return myRoot;
  }

  public void getChildren(final ASTNode astNode, final List<ASTNode> into) {
    ASTNode child = astNode.getFirstChildNode();
    if (child == null) return;
    while (child != null) {
      into.add(child);
      child = child.getTreeNext();
    }
  }

  public void disposeChildren(final List<ASTNode> nodes) {
  }
}
