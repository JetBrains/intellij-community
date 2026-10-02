package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IReparseableLeafElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class PyReparseableTokenType extends PyElementType implements IReparseableLeafElementType<ASTNode> {

  public PyReparseableTokenType(@NotNull String debugName) {
    super(debugName);
  }
}
