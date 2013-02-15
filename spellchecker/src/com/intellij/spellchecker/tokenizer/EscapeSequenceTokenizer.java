/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;

/**
 * @author yole
 */
public class EscapeSequenceTokenizer {
  public static void processTextWithOffsets(PsiElement element, TokenConsumer consumer, StringBuilder unescapedText,
                                            int[] offsets, int startOffset) {
    StringBuilder currentToken = new StringBuilder();
    int currentTokenStart = startOffset;
    for (int i = 0; i < unescapedText.length(); i++) {
      if (offsets[i+1]-offsets[i] == 1) {
        if (currentToken.length() == 0) {
          currentTokenStart = offsets[i] + startOffset;
        }
        currentToken.append(unescapedText.charAt(i));
      }
      else {
        if (currentToken.length() > 0) {
          processCurrentToken(element, currentToken, currentTokenStart, consumer);
          currentToken.setLength(0);
        }
      }
    }
    if (currentToken.length() > 0) {
      processCurrentToken(element, currentToken, currentTokenStart, consumer);
    }
  }

  private static void processCurrentToken(PsiElement element,
                                          StringBuilder currentToken,
                                          int currentTokenStart, TokenConsumer consumer) {
    final String token = currentToken.toString();
    // +1 for the starting quote of the string literal
    consumer.consumeToken(element, token, false, currentTokenStart, TextRange.allOf(token), PlainTextSplitter.getInstance());
  }
}
