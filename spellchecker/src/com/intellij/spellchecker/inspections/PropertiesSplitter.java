// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PropertiesSplitter extends BaseSplitter {
  private static final PropertiesSplitter INSTANCE = new PropertiesSplitter();

  public static PropertiesSplitter getInstance() {
    return INSTANCE;
  }

  private static final @NonNls Pattern WORD = Pattern.compile("\\p{L}*");

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || StringUtil.isEmpty(text)) {
      return;
    }
    final IdentifierSplitter splitter = IdentifierSplitter.getInstance();
    try {
      Matcher matcher = WORD.matcher(newBombedCharSequence(text, range));
      while (matcher.find()) {
        if (matcher.end() - matcher.start() < MIN_RANGE_LENGTH) {
          continue;
        }
        TextRange found = matcherRange(range, matcher);
        splitter.split(text, found, consumer);
      }
    }
    catch (TooLongBombedMatchingException ignored) {
    }
  }
}
