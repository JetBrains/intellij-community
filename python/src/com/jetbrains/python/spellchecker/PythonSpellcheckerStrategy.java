// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.spellchecker;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.CommentSplitter;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.SphinxReferences;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public final class PythonSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {
  private static class StringLiteralTokenizer extends Tokenizer<PyStringLiteralExpression> {
    @Override
    public void tokenize(@NotNull PyStringLiteralExpression element, @NotNull TokenConsumer consumer) {
      final Splitter splitter = PlainTextSplitter.getInstance();
      // Sphinx cross-reference roles/directives in docstrings hold code identifiers, not prose: skip them.
      final List<TextRange> markupRanges = DocStringUtil.getParentDefinitionDocString(element) == element
                                           ? SphinxReferences.INSTANCE.findMarkupRanges(element)
                                           : Collections.emptyList();
      for (PyStringElement stringElement : element.getStringElements()) {
        final int stringElementShift = stringElement.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
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
            for (TextRange range : excludeMarkup(escapeAwareRange, markupRanges, stringElementShift)) {
              final String valueText = range.substring(stringElement.getText());
              consumer.consumeToken(stringElement, valueText, false, range.getStartOffset(), TextRange.allOf(valueText), splitter);
            }
          }
        }
      }
    }
  }

  /**
   * Tokenizes a line comment with {@link CommentSplitter}, skipping any Sphinx role/directive markup so referenced
   * identifiers (e.g. {@code # see :py:func:`socket.getdefaulttimeout`}) are not reported as typos.
   */
  private static class SphinxAwareCommentTokenizer extends Tokenizer<PsiComment> {
    @Override
    public void tokenize(@NotNull PsiComment element, @NotNull TokenConsumer consumer) {
      // doccomment chameleon expands as PsiComment inside PsiComment, avoid duplication (see platform CommentTokenizer)
      if (element.getParent() instanceof PsiComment) return;
      final Splitter splitter = CommentSplitter.getInstance();
      final String text = element.getText();
      final List<TextRange> markupRanges = SphinxReferences.INSTANCE.findMarkupRanges(element);
      for (TextRange range : excludeMarkup(new TextRange(0, text.length()), markupRanges, 0)) {
        final String valueText = range.substring(text);
        consumer.consumeToken(element, valueText, false, range.getStartOffset(), TextRange.allOf(valueText), splitter);
      }
    }
  }

  /**
   * Splits {@code range} (in element/string-element coordinates) into the sub-ranges that remain after removing the
   * Sphinx markup ranges. {@code markupRangesInHost} are relative to the host element; {@code shift} is the offset of
   * {@code range}'s coordinate space inside the host (the offset of a string sub-element, or 0 for a comment).
   * <p>
   * The intersection runs in host coordinates. A markup range that belongs to another string sub-element would give a
   * negative offset in {@code range}'s space, and {@link TextRange} rejects that.
   */
  private static @NotNull List<TextRange> excludeMarkup(@NotNull TextRange range,
                                                        @NotNull List<TextRange> markupRangesInHost,
                                                        int shift) {
    if (markupRangesInHost.isEmpty()) {
      return Collections.singletonList(range);
    }
    final TextRange rangeInHost = range.shiftRight(shift);
    final List<TextRange> excludes = new ArrayList<>();
    for (TextRange markup : markupRangesInHost) {
      final TextRange overlap = rangeInHost.intersection(markup);
      if (overlap != null && !overlap.isEmpty()) {
        excludes.add(overlap.shiftLeft(shift));
      }
    }
    if (excludes.isEmpty()) {
      return Collections.singletonList(range);
    }
    excludes.sort(Comparator.comparingInt(TextRange::getStartOffset));
    final List<TextRange> result = new ArrayList<>();
    int cur = range.getStartOffset();
    for (TextRange exclude : excludes) {
      if (exclude.getStartOffset() > cur) {
        result.add(new TextRange(cur, exclude.getStartOffset()));
      }
      cur = Math.max(cur, exclude.getEndOffset());
    }
    if (cur < range.getEndOffset()) {
      result.add(new TextRange(cur, range.getEndOffset()));
    }
    return result;
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

  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled", false);
  }

  private final StringLiteralTokenizer myStringLiteralTokenizer = new StringLiteralTokenizer();
  private final FormatStringTokenizer myFormatStringTokenizer = new FormatStringTokenizer();
  private final SphinxAwareCommentTokenizer mySphinxCommentTokenizer = new SphinxAwareCommentTokenizer();

  @Override
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PyStringLiteralExpression && !useTextLevelSpellchecking()) {
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
    if (element instanceof PsiComment && !useTextLevelSpellchecking()) {
      // Reuse the platform's suppression/shebang handling, then exclude Sphinx markup from the remaining text.
      return super.getTokenizer(element) == EMPTY_TOKENIZER ? EMPTY_TOKENIZER : mySphinxCommentTokenizer;
    }
    return super.getTokenizer(element);
  }
}
