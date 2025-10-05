// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.wordSelection.PlainTextLineSelectioner;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class XmlLineSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(final @NotNull PsiElement e) {
    return e instanceof XmlToken && ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS;
  }

  @Override
  public List<TextRange> select(final @NotNull PsiElement e, final @NotNull CharSequence editorText, final int cursorOffset, final @NotNull Editor editor) {
    return PlainTextLineSelectioner.selectPlainTextLine(e, editorText, cursorOffset);
  }
}