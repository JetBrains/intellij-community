// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DtdSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof XmlAttlistDecl || e instanceof XmlElementDecl;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    PsiElement[] children = e.getChildren();

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken token) {
        if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
          last = token;
          break;
        }
        if (token.getTokenType() == XmlTokenType.XML_ELEMENT_DECL_START ||
            token.getTokenType() == XmlTokenType.XML_ATTLIST_DECL_START
           ) {
          first = token;
        }
      }
    }

    List<TextRange> result = new ArrayList<>(1);
    if (first != null && last != null) {
      final int offset = last.getTextRange().getEndOffset() + 1;
        result.addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(), Math.min(offset, editorText.length())),
                                        false));
    }

    return result;
  }
}