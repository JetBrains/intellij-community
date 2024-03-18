// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.python.reStructuredText.RestLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class RestFormattingModelBuilder implements FormattingModelBuilder, CustomFormattingModelBuilder {

  @Override
  public boolean isEngagedToFormat(PsiElement context) {
    PsiFile file = context.getContainingFile();
    return file != null && file.getLanguage() == RestLanguage.INSTANCE;
  }

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final RestBlock block = new RestBlock(formattingContext.getNode(), null, Indent.getNoneIndent(), null);
    return FormattingModelProvider
      .createFormattingModelForPsiFile(formattingContext.getContainingFile(), block, formattingContext.getCodeStyleSettings());
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    final PsiElement element = elementAtOffset.getPsi();
    final PsiElement container = element.getParent();
    return container != null ? container.getTextRange() : null;
  }
}
