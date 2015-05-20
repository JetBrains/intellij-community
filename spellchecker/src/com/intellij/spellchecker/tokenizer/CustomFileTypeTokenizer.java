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
package com.intellij.spellchecker.tokenizer;

import com.intellij.ide.highlighter.custom.CustomFileTypeLexer;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
class CustomFileTypeTokenizer extends Tokenizer<PsiElement> {
  private final SyntaxTable mySyntaxTable;

  public CustomFileTypeTokenizer(@NotNull SyntaxTable syntaxTable) {
    mySyntaxTable = syntaxTable;
  }

  @Override
  public void tokenize(@NotNull PsiElement element, TokenConsumer consumer) {
    CustomFileTypeLexer lexer = new CustomFileTypeLexer(mySyntaxTable);
    String text = element.getText();
    lexer.start(text);
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        break;
      }

      if (!isKeyword(tokenType)) {
        consumer.consumeToken(element, text, false, 0, new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()), PlainTextSplitter.getInstance());
      }
      lexer.advance();
    }
  }

  private static boolean isKeyword(IElementType tokenType) {
    return tokenType == CustomHighlighterTokenType.KEYWORD_1 ||
          tokenType == CustomHighlighterTokenType.KEYWORD_2 ||
          tokenType == CustomHighlighterTokenType.KEYWORD_3 ||
          tokenType == CustomHighlighterTokenType.KEYWORD_4;
  }
}
