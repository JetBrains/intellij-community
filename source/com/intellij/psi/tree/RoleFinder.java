package com.intellij.psi.tree;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;

public interface RoleFinder {
  TreeElement findChild(CompositeElement parent);
}
