package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.util.diff.DiffTreeStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ASTDiffTreeStructure implements DiffTreeStructure<ASTNode> {
  private static List<ASTNode> EMPTY = Collections.emptyList();

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

  public List<ASTNode> getChildren(ASTNode astNode) {
    ASTNode child = astNode.getFirstChildNode();
    if (child == null) return EMPTY;
    List<ASTNode> children = new ArrayList<ASTNode>();
    while (child != null) {
      children.add(child);
      child = child.getTreeNext();
    }

    return children;
  }
}
