package com.intellij.psi.tree;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.lang.ASTNode;

public interface RoleFinder {
  ASTNode findChild(ASTNode parent);
}
