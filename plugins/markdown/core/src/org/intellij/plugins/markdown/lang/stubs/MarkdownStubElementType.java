// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class MarkdownStubElementType<S extends MarkdownStubElement<?>, T extends MarkdownPsiElement> extends IStubElementType<S, T> {
  public MarkdownStubElementType(@NotNull @NonNls String debugName) {
    super(debugName, MarkdownLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "markdown: " + super.toString();
  }

  @NotNull
  public abstract PsiElement createElement(@NotNull final ASTNode node);

  @NotNull
  @Override
  public String getExternalId() {
    return "markdown." + super.toString();
  }
}