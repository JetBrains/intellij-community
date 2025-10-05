// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlCDATAContentSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof CompositePsiElement &&
           ((CompositePsiElement)e).getElementType() == XmlElementType.XML_CDATA;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    PsiElement[] children = e.getChildren();

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken token) {
        if (token.getTokenType() == XmlTokenType.XML_CDATA_START) {
          first = token.getNextSibling();
        }
        if (token.getTokenType() == XmlTokenType.XML_CDATA_END) {
          last = token.getPrevSibling();
          break;
        }
      }
    }

    if (first != null && last != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(first.getTextRange().getStartOffset(),
                                                    last.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}