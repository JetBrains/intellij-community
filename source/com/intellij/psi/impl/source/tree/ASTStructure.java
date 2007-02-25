package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.util.diff.FlyweightCapableTreeStructure;

/**
 * @author max
 */
public class ASTStructure implements FlyweightCapableTreeStructure<ASTNode> {
  private ASTNode myRoot;

  public ASTStructure(final ASTNode root) {
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

  public void disposeChildren(final ASTNode[] nodes, final int count) {
  }

  public int getChildren(final ASTNode astNode, final Ref<ASTNode[]> into) {
    ASTNode child = astNode.getFirstChildNode();
    if (child == null) return 0;

    ASTNode[] store = into.get();
    if (store == null) {
      store = new ASTNode[10];
      into.set(store);
    }

    int count = 0;
    while (child != null) {
      if (count >= store.length) {
        ASTNode[] newStore = new ASTNode[(count * 3) / 2];
        System.arraycopy(store, 0, newStore, 0, count);
        into.set(newStore);
        store = newStore;
      }
      store[count++] = child;
      child = child.getTreeNext();
    }

    return count;
  }
}
