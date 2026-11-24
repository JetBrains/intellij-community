// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * The Markdown lexer emits one HTML block token per line.
 * Merge these tokens and the line endings between them so HTML parsing preserves multiline content.
 * The line endings after the last HTML line stay separate tokens.
 */
public final class MarkdownHtmlBlockMergingLexer extends LookAheadLexer {
  public MarkdownHtmlBlockMergingLexer(@NotNull Lexer baseLexer) {
    super(baseLexer);
  }

  @Override
  protected void lookAhead(@NotNull Lexer baseLexer) {
    if (baseLexer.getTokenType() != MarkdownTokenTypes.HTML_BLOCK_CONTENT) {
      advanceLexer(baseLexer);
      return;
    }
    int htmlEnd = baseLexer.getTokenEnd();
    baseLexer.advance();
    IntList trailingEolEnds = new IntArrayList();
    IElementType type = baseLexer.getTokenType();
    while (type == MarkdownTokenTypes.HTML_BLOCK_CONTENT || type == MarkdownTokenTypes.EOL) {
      if (type == MarkdownTokenTypes.HTML_BLOCK_CONTENT) {
        htmlEnd = baseLexer.getTokenEnd();
        trailingEolEnds.clear();
      }
      else {
        trailingEolEnds.add(baseLexer.getTokenEnd());
      }
      baseLexer.advance();
      type = baseLexer.getTokenType();
    }
    addToken(htmlEnd, MarkdownTokenTypes.HTML_BLOCK_CONTENT);
    for (int i = 0; i < trailingEolEnds.size(); i++) {
      addToken(trailingEolEnds.getInt(i), MarkdownTokenTypes.EOL);
    }
  }
}
