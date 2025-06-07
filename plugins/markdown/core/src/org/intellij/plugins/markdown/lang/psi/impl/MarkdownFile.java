// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourUtil;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

public class MarkdownFile extends PsiFileBase implements MarkdownPsiElement {
  private final @NotNull MarkdownFlavourDescriptor flavour;

  public MarkdownFile(@NotNull FileViewProvider viewProvider) {
    this(viewProvider, MarkdownFlavourUtil.obtainDefaultMarkdownFlavour());
  }

  public MarkdownFile(@NotNull FileViewProvider viewProvider, @NotNull MarkdownFlavourDescriptor flavour) {
    super(viewProvider, MarkdownLanguage.INSTANCE);
    this.flavour = flavour;
    // For compatibility only
    putUserData(MarkdownParserManager.FLAVOUR_DESCRIPTION, flavour);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitMarkdownFile(this);
      return;
    }
    visitor.visitFile(this);
  }

  @Override
  public @NotNull FileType getFileType() {
    return MarkdownFileType.INSTANCE;
  }

  public @NotNull MarkdownFlavourDescriptor getFlavour() {
    return flavour;
  }
}
