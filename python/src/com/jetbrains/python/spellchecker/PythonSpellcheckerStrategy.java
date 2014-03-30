/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.spellchecker;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PythonSpellcheckerStrategy extends SpellcheckingStrategy {
  private static class StringLiteralTokenizer extends Tokenizer<PyStringLiteralExpression> {
    @Override
    public void tokenize(@NotNull PyStringLiteralExpression element, TokenConsumer consumer) {
      Splitter splitter = PlainTextSplitter.getInstance();
      String text = element.getText();
      if (text.indexOf('\\') >= 0) {
        for (Pair<TextRange, String> fragment : element.getDecodedFragments()) {
          final String value = fragment.getSecond();
          consumer.consumeToken(element, value, false, fragment.getFirst().getStartOffset(), TextRange.allOf(value),
                                PlainTextSplitter.getInstance());
        }
      }
      else if (text.startsWith("u") || text.startsWith("U") || text.startsWith("r") || text.startsWith("R") ||
          text.startsWith("b") || text.startsWith("B")) {
        List<TextRange> valueTextRanges = element.getStringValueTextRanges();
        for (TextRange valueTextRange : valueTextRanges) {
          final int startOffset = valueTextRange.getStartOffset();
          consumer.consumeToken(element, valueTextRange.substring(element.getText()), false, startOffset, valueTextRange.shiftRight(-startOffset), splitter);
        }
      }
      else {
        consumer.consumeToken(element, splitter);
      }
    }
  }

  private static class FormatStringTokenizer extends Tokenizer<PyStringLiteralExpression> {
    @Override
    public void tokenize(@NotNull PyStringLiteralExpression element, TokenConsumer consumer) {
      String stringValue = element.getStringValue();
      List<PyStringFormatParser.FormatStringChunk> chunks = PyStringFormatParser.parsePercentFormat(stringValue);
      Splitter splitter = PlainTextSplitter.getInstance();
      for (PyStringFormatParser.FormatStringChunk chunk : chunks) {
        if (chunk instanceof PyStringFormatParser.ConstantChunk) {
          int startIndex = element.valueOffsetToTextOffset(chunk.getStartIndex());
          int endIndex = element.valueOffsetToTextOffset(chunk.getEndIndex());
          String text = element.getText().substring(startIndex, endIndex);
          consumer.consumeToken(element, text, false, startIndex, TextRange.allOf(text), splitter);
        }
      }
    }
  }

  private StringLiteralTokenizer myStringLiteralTokenizer = new StringLiteralTokenizer();
  private FormatStringTokenizer myFormatStringTokenizer = new FormatStringTokenizer();

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      PsiElement parent = element.getParent();
      if (parent instanceof PyBinaryExpression) {
        PyBinaryExpression binaryExpression = (PyBinaryExpression)parent;
        if (element == binaryExpression.getLeftExpression() && binaryExpression.getOperator() == PyTokenTypes.PERC) {
          return myFormatStringTokenizer;
        }
      }
      return myStringLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }
}
