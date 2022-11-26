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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.xml.XmlTokenType;

public class XmlQuoteHandler implements QuoteHandler {
  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return true;
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return false;
  }
}
