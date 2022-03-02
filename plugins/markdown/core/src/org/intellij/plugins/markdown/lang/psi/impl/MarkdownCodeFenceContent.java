package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class MarkdownCodeFenceContent extends LeafPsiElement {
  public MarkdownCodeFenceContent(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }
}
