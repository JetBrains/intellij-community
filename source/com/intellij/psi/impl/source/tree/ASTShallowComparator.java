package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.util.diff.ShallowNodeComparator;

/**
 * @author max
 */
public class ASTShallowComparator implements ShallowNodeComparator<ASTNode, ASTNode> {
  public ThreeState deepEqual(final ASTNode oldNode, final ASTNode newNode) {
    if (!typesEqual(oldNode, newNode)) return ThreeState.NO;

    return textMatches(oldNode, newNode);
  }

  private static ThreeState textMatches(final ASTNode oldNode, final ASTNode newNode) {
    if (oldNode instanceof ChameleonElement) {
      return ((TreeElement)newNode).textMatches(oldNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (newNode instanceof ChameleonElement) {
      return ((TreeElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (oldNode instanceof LeafElement) {
      return ((LeafElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
    }

    return ThreeState.UNSURE;
  }

  public boolean typesEqual(final ASTNode n1, final ASTNode n2) {
    if (n1 instanceof LeafPsiElement && n2 instanceof LeafPsiElement) {
      return ((LeafPsiElement)n1).getTypeIndex() == ((LeafPsiElement)n2).getTypeIndex(); // Optimization
    }

    return n1.getElementType() == n2.getElementType();
  }

  public boolean hashcodesEqual(final ASTNode n1, final ASTNode n2) {
    if (n1 instanceof LeafElement && n2 instanceof LeafElement) {
      return textMatches(n1, n2) == ThreeState.YES;
    }

    return ((TreeElement)n1).hc() == ((TreeElement)n2).hc();
  }
}
