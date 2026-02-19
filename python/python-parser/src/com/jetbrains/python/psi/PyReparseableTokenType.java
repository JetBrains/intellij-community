package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IReparseableLeafElementType;
import org.jetbrains.annotations.NotNull;

public abstract class PyReparseableTokenType extends PyElementType implements IReparseableLeafElementType<ASTNode> {

  public PyReparseableTokenType(@NotNull String debugName) {
    super(debugName);
  }
}
