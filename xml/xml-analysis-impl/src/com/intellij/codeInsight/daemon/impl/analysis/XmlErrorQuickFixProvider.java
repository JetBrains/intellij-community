// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

public class XmlErrorQuickFixProvider implements ErrorQuickFixProvider {

  @Override
  public void registerErrorQuickFix(@NotNull final PsiErrorElement element, @NotNull final HighlightInfo.Builder highlightInfo) {
    if (PsiTreeUtil.getParentOfType(element, XmlTag.class) == null) {
      return;
    }
    final String text = element.getErrorDescription();
    if (text.equals(XmlPsiBundle.message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"))) {
      highlightInfo.registerFix(
        new EscapeCharacterIntentionFix(element, new TextRange(0, 1), "&", "&amp;"),
        null, null, null, null);
    }
  }
}
