// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class YAMLQuotedTextTextEvaluator extends YamlScalarTextEvaluator<YAMLQuotedTextImpl> {

  YAMLQuotedTextTextEvaluator(YAMLQuotedTextImpl text) {
    super(text);
  }

  @Override
  public @NotNull List<TextRange> getContentRanges() {
    final ASTNode firstContentNode = myHost.getFirstContentNode();
    if (firstContentNode == null) {
      return Collections.emptyList();
    }

    List<TextRange> result = new ArrayList<>();
    TextRange contentRange = TextRange.create(firstContentNode.getStartOffset(), myHost.getTextRange().getEndOffset())
      .shiftRight(-myHost.getTextRange().getStartOffset());

    final List<String> lines = StringUtil.split(contentRange.substring(myHost.getText()), "\n", true, false);
    // First line has opening quote
    int cumulativeOffset = contentRange.getStartOffset();
    for (int i = 0; i < lines.size(); ++i) {
      final String line = lines.get(i);

      int lineStart = 0;
      int lineEnd = line.length();
      if (i == 0) {
        lineStart++;
      }
      else {
        while (lineStart < line.length() && YAMLGrammarCharUtil.isSpaceLike(line.charAt(lineStart))) {
          lineStart++;
        }
      }
      if (i == lines.size() - 1) {
        // Last line has closing quote
        lineEnd--;
      }
      else {
        while (lineEnd > lineStart && YAMLGrammarCharUtil.isSpaceLike(line.charAt(lineEnd - 1))) {
          lineEnd--;
        }
      }

      result.add(TextRange.create(lineStart, lineEnd).shiftRight(cumulativeOffset));
      cumulativeOffset += line.length() + 1;
    }

    return result;
  }

  @Override
  protected @NotNull String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
    final TextRange leftRange = contentRanges.get(indexBefore);
    if (leftRange.isEmpty() || !myHost.isSingleQuote() && text.charAt(leftRange.getEndOffset() - 1) == '\\') {
      return "\n";
    }
    else if (contentRanges.get(indexBefore + 1).isEmpty()) {
      return "";
    }
    else {
      return " ";
    }
  }
  
}
