/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import java.util.regex.Pattern;

public class PlainTextSplitter extends BaseSplitter {
  private static final PlainTextSplitter INSTANCE = new PlainTextSplitter();

  public static PlainTextSplitter getInstance() {
    return INSTANCE;
  }

  @NonNls
  private static final Pattern MAIL =
    Pattern.compile("([\\p{L}0-9\\.\\-\\_]+@([\\p{L}0-9\\-\\_]+\\.)+(com|net|[a-z]{2}))");

  @NonNls
  private static final Pattern URL =
    Pattern.compile("((ftp|http|file|https)://([^/]+)(/\\w*)?(/\\w*))");


  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || StringUtil.isEmpty(text)) {
      return;
    }
    if (Verifier.checkCharacterData(range.substring(text)) != null) {
      return;
    }

    List<TextRange> toCheck;
    if (text.indexOf('@')>0) {
      toCheck = excludeByPattern(text, range, MAIL, 0);
    }
    else
    if (text.indexOf(':')>0) {
      toCheck = excludeByPattern(text, range, URL, 0);
    }
    else
    {
      toCheck = Collections.singletonList(range);
    }

    final TextSplitter ws = TextSplitter.getInstance();
    for (TextRange r : toCheck) {

      checkCancelled();

      ws.split(text, r, consumer);
    }
  }
}
