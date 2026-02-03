package com.jetbrains.python.psi.impl;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyPlainStringElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyPlainStringElementImpl extends LeafPsiElement implements PyPlainStringElement {
  public PyPlainStringElementImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }
}
