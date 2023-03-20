package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class MarkdownCodeFenceContent extends MarkdownLeafPsiElement {
  public MarkdownCodeFenceContent(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }
}
