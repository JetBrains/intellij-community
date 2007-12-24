package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;

public class TagDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  public TextRange getDeclarationRange(@NotNull final PsiElement container) {
    XmlTag xmlTag = (XmlTag)container;
    int endOffset = xmlTag.getTextRange().getStartOffset();

    for (PsiElement child = xmlTag.getFirstChild(); child != null; child = child.getNextSibling()) {
      endOffset = child.getTextRange().getEndOffset();
      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;
        IElementType tokenType = token.getTokenType();
        if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END || tokenType == XmlTokenType.XML_TAG_END) break;
      }
    }

    return new TextRange(xmlTag.getTextRange().getStartOffset(), endOffset);
  }
}
