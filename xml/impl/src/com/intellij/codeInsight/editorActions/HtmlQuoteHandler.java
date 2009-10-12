/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author peter
*/
public class HtmlQuoteHandler implements QuoteHandler {
  private static QuoteHandler ourStyleQuoteHandler;
  private QuoteHandler myBaseQuoteHandler;
  private static QuoteHandler ourScriptQuoteHandler;

  public HtmlQuoteHandler() {
    this(new XmlQuoteHandler());
  }

  public HtmlQuoteHandler(QuoteHandler _baseHandler) {
    myBaseQuoteHandler = _baseHandler;
  }

  public static void setStyleQuoteHandler(QuoteHandler quoteHandler) {
    ourStyleQuoteHandler = quoteHandler;
  }

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isClosingQuote(iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isClosingQuote(iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isClosingQuote(iterator, offset)) {
      return true;
    }
    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isOpeningQuote(iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isOpeningQuote(iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isOpeningQuote(iterator, offset)) {
      return true;
    }

    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
      return true;
    }

    return false;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    if (myBaseQuoteHandler.isInsideLiteral(iterator)) return true;

    if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isInsideLiteral(iterator)) {
      return true;
    }

    if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isInsideLiteral(iterator)) {
      return true;
    }

    return false;
  }

  public static void setScriptQuoteHandler(QuoteHandler scriptQuoteHandler) {
    ourScriptQuoteHandler = scriptQuoteHandler;
  }
}
