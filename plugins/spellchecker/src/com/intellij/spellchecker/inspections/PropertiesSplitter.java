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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertiesSplitter extends BaseSplitter {


  @NonNls
  private static final Pattern WORD = Pattern.compile("\\p{L}*");


  public List<CheckArea> split(@Nullable String text, @NotNull TextRange range) {
    if (text == null || StringUtil.isEmpty(text)) {
      return null;
    }
    List<CheckArea> results = new ArrayList<CheckArea>();
    final IdentifierSplitter splitter = SplitterFactory.getInstance().getIdentifierSplitter();
    Matcher matcher = WORD.matcher(range.substring(text));
    while (matcher.find()) {
      if (matcher.end() - matcher.start() < MIN_RANGE_LENGTH) {
        continue;
      }
      TextRange found = matcherRange(range, matcher);
      final List<CheckArea> res = splitter.split(text, found);
      if (res != null) {
        results.addAll(res);
      }

    }

    return (results.size() == 0) ? null : results;
  }
}
