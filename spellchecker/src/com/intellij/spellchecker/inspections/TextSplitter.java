// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSplitter extends BaseSplitter {
  private static final TextSplitter INSTANCE = new TextSplitter();

  public static TextSplitter getInstance() {
    return INSTANCE;
  }

  private static final String letter = "(\\p{L}\\p{Mn}*)";
  private static final String xmlEntity = "(&.+?;)";
  private static final String rightSingleQuotationMark = "\\u2019";

  // using possessive quantifiers ++ and *+ to avoid SOE on large inputs
  // see https://blog.sonarsource.com/crafting-regexes-to-avoid-stack-overflows/
  private static final Pattern EXTENDED_WORD_AND_SPECIAL = Pattern.compile(
    xmlEntity + "|" +
    "(#|0x\\d*)?" + // an optional prefix
    letter + "++" + // some letters
    "(['" + rightSingleQuotationMark + "]" + letter + ")?" + // if there's an apostrophe, it should be followed by a letter
    "(_|" + letter + ")*+" // more letters and underscores
  );
  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || StringUtil.isEmpty(text)) {
      return;
    }
    doSplit(text, range, consumer);
  }

  protected void doSplit(@NotNull String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    final WordSplitter ws = WordSplitter.getInstance();
    try {
      Matcher matcher = getExtendedWordAndSpecial().matcher(newBombedCharSequence(text));

      matcher.region(range.getStartOffset(), range.getEndOffset());
      while (matcher.find()) {
        TextRange found = new TextRange(matcher.start(), matcher.end());
        ws.split(text, found, consumer);
      }
    }
    catch (TooLongBombedMatchingException ignored) {
    }
  }

  @Contract(pure = true)
  protected @NotNull Pattern getExtendedWordAndSpecial() {
    return EXTENDED_WORD_AND_SPECIAL;
  }
}