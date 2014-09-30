/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IndentEatingLexer extends MasqueradingLexer {
  public IndentEatingLexer(@NotNull Lexer delegate, int baseIndent) {
    super(new MyLexer(delegate, baseIndent));
  }


  @Nullable
  @Override
  public IElementType getMasqueTokenType() {
    if (isForeignToken()) {
      return null;
    }

    return getTokenType();
  }

  @Nullable
  @Override
  public String getMasqueTokenText() {
    if (getMasqueTokenType() == null) {
      return null;
    }

    return getTokenText();
  }

  public boolean isForeignToken() {
    return myDelegate.getTokenType() == TokenType.DUMMY_HOLDER;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    final IElementType type = myDelegate.getTokenType();
    if (type == TokenType.DUMMY_HOLDER) {
      return TokenType.WHITE_SPACE;
    }
    return type;
  }

  private static class MyLexer extends LayeredLexer {

    public MyLexer(Lexer baseLexer, int indent) {
      super(baseLexer);

      registerLayer(new MyWhiteSpaceLexer(indent), TokenType.WHITE_SPACE);
    }

    private class MyWhiteSpaceLexer extends LexerBase {
      int myIndent;

      CharSequence myBuffer;
      int myState;
      int myStart;
      int myEnd;

      int[] myPositions = new int[0];

      public MyWhiteSpaceLexer(int indent) {
        myIndent = indent;
      }

      @Override
      public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer = buffer;
        myState = initialState;
        myStart = startOffset;
        myEnd = endOffset;

        initPositions();
      }

      private void initPositions() {
        final String s = myBuffer.subSequence(myStart, myEnd).toString();
        // next after newline
        final int i = s.lastIndexOf('\n') + 1;

        if (i == 0 || i == s.length()) {
          myPositions = new int[]{myStart, myEnd};
        }
        else if (s.length() - i > myIndent) {
          myPositions = new int[]{myStart, myStart + i, myStart + i + myIndent, myEnd};
        }
        else {
          myPositions = new int[]{myStart, myStart + i, myEnd};
        }
      }

      @Override
      public int getState() {
        return myState;
      }

      @Nullable
      @Override
      public IElementType getTokenType() {
        if (myState + 1 >= myPositions.length) {
          return null;
        }
        if (myState == 1) {
          return TokenType.DUMMY_HOLDER;
        }
        return TokenType.WHITE_SPACE;
      }

      @Override
      public int getTokenStart() {
        return myPositions[myState];
      }

      @Override
      public int getTokenEnd() {
        if (myState + 1 >= myPositions.length) {
          return myEnd;
        }
        return myPositions[myState + 1];
      }

      @Override
      public void advance() {
        myState++;
      }

      @NotNull
      @Override
      public CharSequence getBufferSequence() {
        return myBuffer;
      }

      @Override
      public int getBufferEnd() {
        return myEnd;
      }
    }
  }
}
