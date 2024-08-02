// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.spellchecker;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;


public final class PythonSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {
  private static class StringLiteralTokenizer extends Tokenizer<PyStringLiteralExpression> {
    @Override
    public void tokenize(@NotNull PyStringLiteralExpression element, @NotNull TokenConsumer consumer) {
      final Splitter splitter = PlainTextSplitter.getInstance();
      for (PyStringElement stringElement : element.getStringElements()) {
        final List<TextRange> literalPartRanges;
        if (stringElement.isFormatted()) {
          literalPartRanges = ((PyFormattedStringElement)stringElement).getLiteralPartRanges();
        }
        else {
          literalPartRanges = Collections.singletonList(stringElement.getContentRange());
        }
        final PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(stringElement);
        final boolean containsEscapes = stringElement.textContains('\\');
        for (TextRange literalPartRange : literalPartRanges) {
          final List<TextRange> escapeAwareRanges;
          if (stringElement.isRaw() || !containsEscapes) {
            escapeAwareRanges = Collections.singletonList(literalPartRange);
          }
          else {
            escapeAwareRanges = ContainerUtil.map(decoder.decodeRange(literalPartRange), x -> x.getFirst());
          }
          for (TextRange escapeAwareRange : escapeAwareRanges) {
            final String valueText = escapeAwareRange.substring(stringElement.getText());
            consumer.consumeToken(stringElement, valueText, false, escapeAwareRange.getStartOffset(), TextRange.allOf(valueText), splitter);
          }
        }
      }
    }
  }

  private static class FormatStringTokenizer extends Tokenizer<PyStringLiteralExpression> {
    @Override
    public void tokenize(@NotNull PyStringLiteralExpression element, @NotNull TokenConsumer consumer) {
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

  private final StringLiteralTokenizer myStringLiteralTokenizer = new StringLiteralTokenizer();
  private final FormatStringTokenizer myFormatStringTokenizer = new FormatStringTokenizer();

  @Override
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(element.getProject());
      if (element.getTextLength() >= 2 && injectionManager.getInjectedPsiFiles(element) != null) {
        return EMPTY_TOKENIZER;
      }
      PsiElement parent = element.getParent();
      if (parent instanceof PyBinaryExpression binaryExpression) {
        if (element == binaryExpression.getLeftExpression() && binaryExpression.getOperator() == PyTokenTypes.PERC) {
          return myFormatStringTokenizer;
        }
      }
      return myStringLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }
}
