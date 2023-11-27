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
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.progress.ProcessCanceledException;
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
  // using possessive quantifiers ++ and *+ to avoid SOE on large inputs
  // see https://blog.sonarsource.com/crafting-regexes-to-avoid-stack-overflows/
  private static final Pattern EXTENDED_WORD_AND_SPECIAL = Pattern.compile(
    xmlEntity + "|" +
    "(#|0x\\d*)?" + // an optional prefix
    letter + "++" + // some letters
    "('" + letter + ")?" + // if there's an apostrophe, it should be followed by a letter
    "(_|" + letter + ")*+" // more letters and underscores
  );
  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (StringUtil.isEmpty(text)) {
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
    catch (ProcessCanceledException ignored) {
    }
  }

  @NotNull
  @Contract(pure = true)
  protected Pattern getExtendedWordAndSpecial() {
    return EXTENDED_WORD_AND_SPECIAL;
  }
}