// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WordSplitter extends BaseSplitter {
  private static final WordSplitter INSTANCE = new WordSplitter();

  public static WordSplitter getInstance() {
    return INSTANCE;
  }

  private static final @NonNls Pattern SPECIAL = Pattern.compile("&\\p{Alnum}{2};?|#\\p{Alnum}{3,6}|0x\\p{Alnum}?");

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || range.getLength() <= 1) {
      return;
    }

    try {
      Matcher specialMatcher = SPECIAL.matcher(newBombedCharSequence(text));
      specialMatcher.region(range.getStartOffset(), range.getEndOffset());
      if (specialMatcher.find()) {
        TextRange found = new TextRange(specialMatcher.start(), specialMatcher.end());
        addWord(consumer, true, found);
      }
      else {
        IdentifierSplitter.getInstance().split(text, range, consumer);
      }
    }
    catch (TooLongBombedMatchingException ignored) {
    }
  }
}
