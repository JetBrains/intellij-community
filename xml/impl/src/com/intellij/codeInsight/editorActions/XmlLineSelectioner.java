// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.editorActions.wordSelection.PlainTextLineSelectioner;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class XmlLineSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull final PsiElement e) {
    return e instanceof XmlToken && ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS;
  }

  @Override
  public List<TextRange> select(@NotNull final PsiElement e, @NotNull final CharSequence editorText, final int cursorOffset, @NotNull final Editor editor) {
    return PlainTextLineSelectioner.selectPlainTextLine(e, editorText, cursorOffset);
  }
}