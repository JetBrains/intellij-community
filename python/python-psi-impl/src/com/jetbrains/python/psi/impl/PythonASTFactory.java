package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public final class PythonASTFactory extends ASTFactory {

  @Nullable
  @Override
  public LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    if (PyTokenTypes.STRING_NODES.contains(type)) {
      return new PyPlainStringElementImpl(type, text);
    }
    return super.createLeaf(type, text);
  }
}
