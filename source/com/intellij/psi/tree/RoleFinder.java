package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public interface RoleFinder {
  ASTNode findChild(@NotNull ASTNode parent);
}
