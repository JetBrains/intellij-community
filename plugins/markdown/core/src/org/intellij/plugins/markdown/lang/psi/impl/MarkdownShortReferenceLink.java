// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiExternalReferenceHost;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

public class MarkdownShortReferenceLink extends ASTWrapperPsiElement implements MarkdownPsiElement, PsiExternalReferenceHost {
  public MarkdownShortReferenceLink(@NotNull ASTNode node) {
    super(node);
  }
}