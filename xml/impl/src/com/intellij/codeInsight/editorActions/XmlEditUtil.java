/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xml.util.HtmlUtil.hasHtml;
import static com.intellij.xml.util.HtmlUtil.supportsXmlTypedHandlers;

public class XmlEditUtil {
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

  @NotNull
  private static CodeStyleSettings.QuoteStyle getQuoteStyleForFile(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file)
      .getCustomSettings(HtmlCodeStyleSettings.class)
      .HTML_QUOTE_STYLE;
  }
}
