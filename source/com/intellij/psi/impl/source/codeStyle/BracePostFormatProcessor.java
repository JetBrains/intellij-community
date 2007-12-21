package com.intellij.psi.impl.source.codeStyle;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.util.TextRange;

public class BracePostFormatProcessor implements PostFormatProcessor {
  public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
    return new BraceEnforcer(settings).process(source);
  }

  public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
    return new BraceEnforcer(settings).processText(source, rangeToReformat);
  }
}
