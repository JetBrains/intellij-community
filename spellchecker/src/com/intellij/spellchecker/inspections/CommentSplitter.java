// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentSplitter extends BaseSplitter {
  private static final Matcher HTML = Pattern.compile("<(\\S+?)[^<>]*?>(.*?)</\\1>").matcher("");
  
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
