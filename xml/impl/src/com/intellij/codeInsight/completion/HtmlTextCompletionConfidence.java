// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class HtmlTextCompletionConfidence extends CompletionConfidence {
  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (HtmlCompletionContributor.isHtmlElementInTextCompletionAutoPopupEnabledForFile(psiFile)) {
      return notAfterASpace(psiFile, offset);
    }
    return shouldSkipAutopopupInHtml(contextElement, offset) ? ThreeState.YES : ThreeState.UNSURE;
  }

  private static ThreeState notAfterASpace(PsiFile file, int offset) {
    if (offset <= 0 || file.getText().charAt(offset - 1) != ' ') {
      return ThreeState.UNSURE;
    }
    return ThreeState.YES;
  }

  public static boolean shouldSkipAutopopupInHtml(@NotNull PsiElement contextElement, int offset) {
    ASTNode node = contextElement.getNode();
    if (node != null && node.getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
      PsiElement parent = contextElement.getParent();
      if (parent instanceof XmlText || parent instanceof XmlDocument) {
        String contextElementText = contextElement.getText();
        int endOffset = offset - contextElement.getTextRange().getStartOffset();
        String prefix = contextElementText.substring(0, Math.min(contextElementText.length(), endOffset));
        return !StringUtil.startsWithChar(prefix, '<') && !StringUtil.startsWithChar(prefix, '&');
      }
    }
    return false;
  }
}
