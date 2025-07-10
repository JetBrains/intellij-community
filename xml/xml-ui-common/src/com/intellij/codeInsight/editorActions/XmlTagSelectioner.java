// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlTagSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof XmlTag;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    PsiElement[] children = e.getChildren();

    addTagContentSelection(children, result, editorText);

    PsiElement prev = e.getPrevSibling();
    while (prev instanceof PsiWhiteSpace || prev instanceof XmlText || prev instanceof XmlComment) {
      if (prev instanceof XmlText && !prev.getText().trim().isEmpty()) break;
      if (prev instanceof XmlComment) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(prev.getTextRange().getStartOffset(),
                                                      e.getTextRange().getEndOffset()),
                                        false));
      }
      prev = prev.getPrevSibling();
    }

    return result;
  }

  private static void addTagContentSelection(final PsiElement[] children, final List<? super TextRange> result, final CharSequence editorText) {
    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken token) {
        if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
          first = token.getNextSibling();
        }
        if (token.getTokenType() == XmlTokenType.XML_END_TAG_START) {
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
  }
}