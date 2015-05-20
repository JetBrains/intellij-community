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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class EscapeSequenceTokenizer<T extends PsiElement> extends Tokenizer<T> {
  private static Key<int[]> ESCAPE_OFFSETS = Key.create("escape.tokenizer.offsets");

  public static void processTextWithOffsets(PsiElement element, TokenConsumer consumer, StringBuilder unescapedText,
                                            int[] offsets, int startOffset) {
    if (element != null) element.putUserData(ESCAPE_OFFSETS, offsets);
    final String text = unescapedText.toString();
    consumer.consumeToken(element, text, false, startOffset, TextRange.allOf(text), PlainTextSplitter.getInstance());
    if (element != null) element.putUserData(ESCAPE_OFFSETS, null);
  }

  @NotNull
  public TextRange getHighlightingRange(PsiElement element, int offset, TextRange range) {
    final int[] offsets = element.getUserData(ESCAPE_OFFSETS);
    if (offsets != null) {
      int start = offsets[range.getStartOffset()];
      int end = offsets[range.getEndOffset()];

      return new TextRange(offset + start, offset + end);
    }
    return super.getHighlightingRange(element, offset, range);
  }
}
