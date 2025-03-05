// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.lexer.MarkdownLexer;
import org.intellij.plugins.markdown.lang.MarkdownElementType;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownLexerAdapter extends LexerBase {
  private final @NotNull MarkdownLexer delegateLexer = MarkdownParserManager.FLAVOUR.createInlinesLexer();

  private int endOffset;

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.endOffset = endOffset;
    delegateLexer.start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public int getState() {
    return 1;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return MarkdownElementType.platformType(delegateLexer.getType());
  }

  @Override
  public int getTokenStart() {
    return delegateLexer.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    return delegateLexer.getTokenEnd();
  }

  @Override
  public void advance() {
    delegateLexer.advance();
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return delegateLexer.getOriginalText();
  }

  @Override
  public int getBufferEnd() {
    return endOffset;
  }
}
