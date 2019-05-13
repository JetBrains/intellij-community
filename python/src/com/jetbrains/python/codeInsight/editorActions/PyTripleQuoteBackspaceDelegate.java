/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.editor.BaseQuoteHandler;

public class PyTripleQuoteBackspaceDelegate extends BackspaceHandlerDelegate {
  private boolean isTripleQuote;

  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    isTripleQuote = false;
    if (c == '"' || c == '\'' && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) {
      final QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(file, editor);
      if (!(quoteHandler instanceof BaseQuoteHandler)) return;

      final int offset = editor.getCaretModel().getCurrentCaret().getOffset();
      String text = editor.getDocument().getText();
      boolean mayBeTripleQuote = offset >= 3 && offset + 2 < text.length();
      if (mayBeTripleQuote) {
        HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
        boolean hasTripleQuoteAfter = offset + 2 < text.length() &&
                                      text.charAt(offset) == c && text.charAt(offset + 1) == c && text.charAt(offset + 2) == c;
        isTripleQuote = quoteHandler.isOpeningQuote(iterator, offset - 1) && hasTripleQuoteAfter;
      }
    }
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    if (isTripleQuote) {
      int offset = editor.getCaretModel().getCurrentCaret().getOffset();
      editor.getDocument().deleteString(offset - 2, offset + 3);
      return true;
    }
    return false;
  }
}
