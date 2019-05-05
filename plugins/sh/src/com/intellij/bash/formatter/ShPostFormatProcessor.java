package com.intellij.bash.formatter;

import com.intellij.bash.psi.ShFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.NotNull;

public class ShPostFormatProcessor implements PostFormatProcessor {
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    return source instanceof ShFile ? source : null;
  }

  @NotNull
  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (!(source instanceof ShFile)) return rangeToReformat;
    TextRange range = ExternalFormatProcessor.formatRangeInFile(source, rangeToReformat, false);
    return range != null ? range : rangeToReformat;
  }
}
