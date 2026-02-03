// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;


import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public class HtmlQuotesFormatPreprocessor implements PreFormatProcessor {
  @Override
  public @NotNull TextRange process(@NotNull ASTNode node, @NotNull TextRange range) {
    PsiElement psiElement = node.getPsi();
    if (psiElement != null &&
        psiElement.isValid() &&
        psiElement.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
      PsiFile file = psiElement.getContainingFile();
      PsiElement fileContext = file.getContext();
      String contextQuote = fileContext != null ? Character.toString(fileContext.getText().charAt(0)) : null;
      CodeStyleSettings rootSettings = CodeStyle.getSettings(file);
      HtmlCodeStyleSettings htmlSettings = rootSettings.getCustomSettings(HtmlCodeStyleSettings.class);
      CodeStyleSettings.QuoteStyle quoteStyle = htmlSettings.HTML_QUOTE_STYLE;
      if (quoteStyle != CodeStyleSettings.QuoteStyle.None
          && htmlSettings.HTML_ENFORCE_QUOTES
          && !StringUtil.equals(quoteStyle.quote, contextQuote)) {
        PostFormatProcessorHelper postFormatProcessorHelper =
          new PostFormatProcessorHelper(rootSettings.getCommonSettings(HTMLLanguage.INSTANCE));
        postFormatProcessorHelper.setResultTextRange(range);
        HtmlQuotesConverter converter = new HtmlQuotesConverter(quoteStyle, psiElement, postFormatProcessorHelper);
        Document document = converter.getDocument();
        if (document != null) {
          DocumentUtil.executeInBulk(document, converter);
        }
        return postFormatProcessorHelper.getResultTextRange();
      }
    }
    return range;
  }
}
