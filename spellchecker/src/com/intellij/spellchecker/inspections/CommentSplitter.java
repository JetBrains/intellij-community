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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public class CommentSplitter extends BaseSplitter {
  private static final Pattern HTML = Pattern.compile("<(\\S+?)[^<>]*?>(.*?)</\\1>");
  
  private static final CommentSplitter INSTANCE = new CommentSplitter();
  
  public static CommentSplitter getInstance() {
    return INSTANCE;
  }

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || StringUtil.isEmpty(text)) {
      return;
    }

    List<TextRange> toCheck = excludeByPattern(text, range, HTML, 2);
    final Splitter ps = PlainTextSplitter.getInstance();
    for (TextRange r : toCheck) {
      ps.split(text, r, consumer);
    }
  }
}
