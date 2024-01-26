// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.xml;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class XmlTokenizerBase<T extends PsiElement> extends Tokenizer<T> {
  public static <T extends PsiElement> XmlTokenizerBase<T> create(Splitter splitter) {
    return new XmlTokenizerBase<>(splitter);
  }

  private final Splitter mySplitter;

  @Override
  public String toString() {
    return "XmlTokenizerBase(splitter=" + mySplitter + ")";
  }

  public XmlTokenizerBase(Splitter splitter) {
    mySplitter = splitter;
  }

  @Override
  public void tokenize(@NotNull T element, @NotNull TokenConsumer consumer) {
    List<TextRange> excludeRanges = getSpellcheckOuterContentRanges(element);
    for (var spellcheckRange : getSpellcheckRanges(element)) {
      consumer.consumeToken(
        element, spellcheckRange.text, spellcheckRange.useRename,
        spellcheckRange.offset, spellcheckRange.rangeToCheck,
        createExclusionAwareSplitter(excludeRanges, spellcheckRange.offset)
      );
    }
  }

  protected @NotNull List<@NotNull SpellcheckRange> getSpellcheckRanges(@NotNull T element) {
    return Collections.singletonList(new SpellcheckRange(element.getText()));
  }

  protected @NotNull List<@NotNull TextRange> getSpellcheckOuterContentRanges(@NotNull T element) {
    PsiLanguageInjectionHost injectionHost;
    if (element instanceof PsiLanguageInjectionHost) {
      injectionHost = (PsiLanguageInjectionHost)element;
    }
    else {
      injectionHost = null;
    }
    if (injectionHost != null) {
      List<TextRange> ranges = new SmartList<>();
      String text = element.getText();
      InjectedLanguageManager.getInstance(injectionHost.getProject()).enumerate(injectionHost, (injectedPsi, places) -> {
        ranges.addAll(ContainerUtil.mapNotNull(places, place -> adjustInjectionRangeForExclusion(text, place.getRangeInsideHost())));
      });
      return ranges;
    }
    return Collections.emptyList();
  }

  protected @Nullable TextRange adjustInjectionRangeForExclusion(@NotNull String text, @NotNull TextRange range) {
    if (range.getStartOffset() >= text.length())
      return null;
    int startOffset = range.getStartOffset();
    int endOffset = Math.min(range.getEndOffset(), text.length() - 1);
    while (startOffset > 0 && !isLetterDigitOrWhitespace(text.charAt(startOffset - 1))) {
      startOffset--;
    }
    while (endOffset < text.length() && !isLetterDigitOrWhitespace(text.charAt(endOffset))) {
      endOffset++;
    }
    if (startOffset < endOffset)
      return TextRange.create(startOffset, endOffset);
    else
      return null;
  }

  private @NotNull Splitter createExclusionAwareSplitter(List<TextRange> excludeRanges, int offset) {
    return new Splitter() {
      @Override
      public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
        mySplitter.split(text, range, tokenRange -> {
          if (ContainerUtil.all(excludeRanges, excludeRange -> !excludeRange.intersects(tokenRange.shiftRight(offset)))) {
            consumer.consume(tokenRange);
          }
        });
      }
    };
  }

  private static boolean isLetterDigitOrWhitespace(char ch) {
    return Character.isLetterOrDigit(ch) || Character.isWhitespace(ch);
  }

  public record SpellcheckRange(String text, boolean useRename, int offset, TextRange rangeToCheck) {
    SpellcheckRange(String text) {
      this(text, false);
    }

    SpellcheckRange(String text, boolean useRename) {
      this(text, useRename, 0, TextRange.allOf(text));
    }
  }
}
