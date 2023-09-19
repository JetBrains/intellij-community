// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class TagDeclarationRangeHandler implements DeclarationRangeHandler {
  @Override
  public @NotNull TextRange getDeclarationRange(final @NotNull PsiElement container) {
    XmlTag xmlTag = (XmlTag)container;
    int endOffset = xmlTag.getTextRange().getStartOffset();

    for (PsiElement child = xmlTag.getFirstChild(); child != null; child = child.getNextSibling()) {
      endOffset = child.getTextRange().getEndOffset();
      if (child instanceof XmlToken token) {
        IElementType tokenType = token.getTokenType();
        if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END || tokenType == XmlTokenType.XML_TAG_END) break;
      }
    }

    return new TextRange(xmlTag.getTextRange().getStartOffset(), endOffset);
  }
}
