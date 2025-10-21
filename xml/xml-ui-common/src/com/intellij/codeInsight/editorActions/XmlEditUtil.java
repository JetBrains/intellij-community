// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xml.util.HtmlUtil.hasHtml;
import static com.intellij.xml.util.HtmlUtil.supportsXmlTypedHandlers;

public final class XmlEditUtil {
  /**
   * Calculates quote style to use in a particular file depends on user's settings and injections
   */
  public static CodeStyleSettings.QuoteStyle quoteStyle(@NotNull PsiFile file) {
    PsiElement context = file.getContext();
    CodeStyleSettings.QuoteStyle style = getQuoteStyleForFile(file);
    if (context != null && !style.quote.isEmpty() && context.getText().startsWith(style.quote)) {
      return style == CodeStyleSettings.QuoteStyle.Double ? CodeStyleSettings.QuoteStyle.Single : CodeStyleSettings.QuoteStyle.Double;
    }
    return style;
  }

  public static String getAttributeQuote(@NotNull PsiFile file) {
    if (hasHtml(file) || supportsXmlTypedHandlers(file)) {
      return getQuoteStyleForFile(file).quote;
    }
    return "\"";
  }

  private static @NotNull CodeStyleSettings.QuoteStyle getQuoteStyleForFile(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file)
      .getCustomSettings(HtmlCodeStyleSettings.class)
      .HTML_QUOTE_STYLE;
  }
}
