// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

final class ShPostFormatProcessor implements PostFormatProcessor {
  @NotNull
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    return source;
  }

  @NotNull
  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!(source instanceof ShFile)) return rangeToReformat;
    TextRange range = ExternalFormatProcessor.formatRangeInFile(source, rangeToReformat, false);
    return range != null ? range : rangeToReformat;
  }
}
