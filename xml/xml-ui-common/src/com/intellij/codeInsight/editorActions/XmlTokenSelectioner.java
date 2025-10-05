// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

class XmlTokenSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof XmlToken &&
           !HtmlSelectioner.canSelectElement(e);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    XmlToken token = (XmlToken)e;

    if (shouldSelectToken(token)) {
      List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
      SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
      return ranges;
    }
    else {
      List<TextRange> result = new ArrayList<>();
      SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, result);
      return result;
    }
  }

  static boolean shouldSelectToken(final XmlToken token) {
    return token.getTokenType() != XmlTokenType.XML_DATA_CHARACTERS &&
          token.getTokenType() != XmlTokenType.XML_START_TAG_START &&
          token.getTokenType() != XmlTokenType.XML_END_TAG_START &&
          token.getTokenType() != XmlTokenType.XML_EMPTY_ELEMENT_END &&
          token.getTokenType() != XmlTokenType.XML_TAG_END;
  }
}