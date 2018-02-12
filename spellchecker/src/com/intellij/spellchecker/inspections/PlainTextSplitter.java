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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jdom.Verifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.io.URLUtil.URL_PATTERN;

public class PlainTextSplitter extends BaseSplitter {
  private static final PlainTextSplitter INSTANCE = new PlainTextSplitter();

  public static PlainTextSplitter getInstance() {
    return INSTANCE;
  }

  @NonNls
  private static final
  Pattern SPLIT_PATTERN = Pattern.compile("(\\s|\b)");

  @NonNls
  private static final Pattern MAIL =
    Pattern.compile("([\\p{L}0-9\\.\\-\\_\\+]+@([\\p{L}0-9\\-\\_]+(\\.)?)+(com|net|[a-z]{2})?)");
  
  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (StringUtil.isEmpty(text)) {
      return;
    }

    final String substring = range.substring(text).replace('\b', '\n').replace('\f', '\n');
    if (Verifier.checkCharacterData(SPLIT_PATTERN.matcher(substring).replaceAll("")) != null) {
      return;
    }

    final TextSplitter ws = TextSplitter.getInstance();
    int from = range.getStartOffset();
    int till;
    Matcher matcher = SPLIT_PATTERN.matcher(range.substring(text));
    while (true) {
      checkCancelled();
      List<TextRange> toCheck;
      TextRange wRange;
      String word;
      if(matcher.find()) {
        TextRange found = matcherRange(range, matcher);
        till = found.getStartOffset();
        if (badSize(from, till)) {
          continue;
        }
        wRange = new TextRange(from, till);
        word = wRange.substring(text);
        from = found.getEndOffset();
      } else { // end hit or zero matches
        wRange = new TextRange(from, range.getEndOffset());
        word = wRange.substring(text);
      }
      if (word.contains("@")) {
        toCheck = excludeByPattern(text, wRange, MAIL, 0);
      }
      else
      if (word.contains("://")) {
        toCheck = excludeByPattern(text, wRange, URL_PATTERN, 0);
      }
      else {
        toCheck = Collections.singletonList(wRange);
      }
      for (TextRange r : toCheck) {
        ws.split(text, r, consumer);
      }
      if(matcher.hitEnd()) break;
    }
  }
}
