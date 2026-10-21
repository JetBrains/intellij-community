// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer;

import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.plugins.markdown.lang.parser.CancellableText;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.jetbrains.annotations.NotNull;

public class MarkdownToplevelLexer extends MarkdownTreeLexerBase {
  private final @NotNull MarkdownParser parser;

  private int myBufferHash = 0;
  private int myBufferStart;
  private int myParsedEnd;

  public MarkdownToplevelLexer() {
    this(MarkdownParserManager.FLAVOUR);
  }

  public MarkdownToplevelLexer(@NotNull MarkdownFlavourDescriptor flavour) {
    parser = MarkdownParserManager.createMarkdownParser(flavour, true);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    final var bufferHash = buffer.hashCode();
    if (bufferHash == myBufferHash && startOffset == myBufferStart && endOffset == getBufferEnd() && buffer.equals(getBufferSequence())) {
      lexemeIndex = 0;
      return;
    }
    myBufferHash = bufferHash;
    myBufferStart = startOffset;
    myParsedEnd = startOffset;
    reset(CancellableText.of(buffer), endOffset);
    addNextTokens();
  }

  @Override
  protected void addNextTokens() {
    if (myParsedEnd < getBufferEnd()) {
      var block = parser.parseBlock(buffer, myParsedEnd);
      addTokens(block.getChildren(), 0);
      myParsedEnd = block.getEndOffset();
    }
  }
}
