// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.embedding;

import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.lexer.LexerPosition;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LazyDelegateLexer extends LexerBase {
  public static final EmptyLexer EMPTY_LEXER = new EmptyLexer();

  private Lexer myDelegate = EMPTY_LEXER;

  protected abstract Lexer createDelegate();

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    if (myDelegate == EMPTY_LEXER) {
      myDelegate = createDelegate();
    }

    myDelegate.start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public @NotNull CharSequence getTokenSequence() {
    return myDelegate.getTokenSequence();
  }

  @Override
  public @NotNull String getTokenText() {
    return myDelegate.getTokenText();
  }

  @Override
  public int getState() {
    return myDelegate.getState();
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  @Override
  public int getTokenStart() {
    return myDelegate.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    return myDelegate.getTokenEnd();
  }

  @Override
  public void advance() {
    myDelegate.advance();
  }

  @Override
  public @NotNull LexerPosition getCurrentPosition() {
    return myDelegate.getCurrentPosition();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    myDelegate.restore(position);
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myDelegate.getBufferSequence();
  }

  @Override
  public int getBufferEnd() {
    return myDelegate.getBufferEnd();
  }
}
