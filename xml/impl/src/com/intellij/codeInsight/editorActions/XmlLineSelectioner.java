/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.editorActions.wordSelection.PlainTextLineSelectioner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
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