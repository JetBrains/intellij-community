// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

final class HtmlClosingTagErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || psiFile.getViewProvider().getBaseLanguage() != HTMLLanguage.INSTANCE
                            && HTMLLanguage.INSTANCE != element.getLanguage()) return true;

    return !skip(element);
  }

  public static boolean skip(@NotNull PsiErrorElement element) {
    final PsiElement[] children = element.getChildren();
    if (children.length > 0) {
      if (children[0] instanceof XmlToken && XmlTokenType.XML_END_TAG_START == ((XmlToken)children[0]).getTokenType()) {
        if (XmlPsiBundle.message("xml.parsing.closing.tag.matches.nothing").equals(element.getErrorDescription())) {
          return true;
        }
      }
    }
    return false;
  }
}
