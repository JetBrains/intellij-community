package org.intellij.plugins.markdown.lang.psi.impl;

import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MarkdownCompositePsiElement extends MarkdownPsiElement {
  String getPresentableTagName();

  @NotNull
  List<MarkdownPsiElement> getCompositeChildren();
}