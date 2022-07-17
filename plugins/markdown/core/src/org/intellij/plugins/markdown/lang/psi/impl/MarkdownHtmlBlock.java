// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownHtmlBlock extends ASTWrapperPsiElement implements MarkdownPsiElement {

  public MarkdownHtmlBlock(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new MarkdownBasePresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return "HTML block";
      }
    };
  }
}
