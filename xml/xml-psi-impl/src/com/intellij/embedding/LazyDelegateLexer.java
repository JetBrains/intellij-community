/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.embedding;

import com.intellij.lexer.*;
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
  @NotNull
  public CharSequence getTokenSequence() {
    return myDelegate.getTokenSequence();
  }

  @Override
  @NotNull
  public String getTokenText() {
    return myDelegate.getTokenText();
  }

  @Override
  public int getState() {
    return myDelegate.getState();
  }

  @Override
  @Nullable
  public IElementType getTokenType() {
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
  @NotNull
  public LexerPosition getCurrentPosition() {
    return myDelegate.getCurrentPosition();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    myDelegate.restore(position);
  }

  @Override
  @NotNull
  public CharSequence getBufferSequence() {
    return myDelegate.getBufferSequence();
  }

  @Override
  public int getBufferEnd() {
    return myDelegate.getBufferEnd();
  }
}
