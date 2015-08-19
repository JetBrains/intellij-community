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

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TemplateMasqueradingLexer extends MasqueradingLexer {
  protected final static IElementType MINUS_TYPE = new IElementType("MINUS", null);

  public TemplateMasqueradingLexer(@NotNull Lexer delegate) {
    super(delegate);
  }

  protected abstract static class MyLexer extends LexerBase {
    protected final int myIndent;
    protected final Lexer myDelegate;

    protected int myStartOffset;
    protected int myEndOffset;
    protected CharSequence myBuffer;

    protected int myState;
    protected IElementType myTokenType;
    protected int myTokenStart;
    protected int myTokenEnd;

    public MyLexer(int indent, Lexer delegateLexer) {
      myIndent = indent;
      myDelegate = delegateLexer;
    }

    protected abstract IElementType getIndentTokenType();
    protected abstract IElementType getEmbeddedContentTokenType();

    protected abstract int getEmbeddedCodeStartMarkerLength();

    protected int getDelegateState(int state) {
      return state;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myState = initialState % 239;

      myTokenEnd = startOffset;

      if (myState == 1) {
        myTokenEnd = findEol(startOffset);
        myDelegate.start(buffer, startOffset, myTokenEnd, getDelegateState(initialState / 239));
      }
      else
      {
        myDelegate.start(buffer, startOffset, startOffset, getDelegateState(0));
        advance();
      }
    }

    @Override
    public int getState() {
      return myDelegate.getState() * 239 + myState;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
      if (myState == 2) {
        return null;
      }
      if (myState == 1) {
        return myDelegate.getTokenType();
      }

      return myTokenType;
    }

    @NotNull
    @Override
    public String getTokenText() {
      if (myState == 2) {
        return "";
      }
      if (myState == 1) {
        return myDelegate.getTokenText();
      }

      return super.getTokenText();
    }

    @Override
    public int getTokenStart() {
      if (myState == 1) {
        return myDelegate.getTokenStart();
      }

      return myTokenStart;
    }

    @Override
    public int getTokenEnd() {
      if (myState == 1) {
        return myDelegate.getTokenEnd();
      }

      return myTokenEnd;
    }

    @Override
    public void advance() {
      if (myState == 1) {
        myDelegate.advance();
        if (myDelegate.getTokenType() == null) {
          myState = 0;
          myTokenEnd = myDelegate.getBufferEnd();
          advance();
        }
      }
      else {
        if (myTokenEnd == myEndOffset) {
          myState = 2;
          return;
        }

        myTokenStart = myTokenEnd;

        if (StringUtil.isWhiteSpace(myBuffer.charAt(myTokenStart))) {
          myTokenType = getIndentTokenType();
          myTokenEnd = findNonWhitespace(myTokenStart);
          return;
        }

        int curIndent = calcIndent(myTokenStart);
        if (curIndent > myIndent) {
          myTokenType = getEmbeddedContentTokenType();
          myTokenEnd = findEndByIndent(myTokenStart + 1);
          return;
        }

        int embeddedCodeStartMarkerLength = getEmbeddedCodeStartMarkerLength();
        if (embeddedCodeStartMarkerLength > 0) {
          myTokenType = MINUS_TYPE;
          myTokenEnd = myTokenStart + embeddedCodeStartMarkerLength;
        }
        else {
          myTokenEnd = findEol(myTokenStart);
          myDelegate.start(myBuffer, myTokenStart, myTokenEnd, getDelegateState(myDelegate.getState()));
          myState = 1;
        }
      }
    }

    protected int findEndByIndent(int offset) {
      while (offset < myEndOffset) {
        if (offset > myStartOffset
            && !StringUtil.isWhiteSpace(myBuffer.charAt(offset))
            && StringUtil.isWhiteSpace(myBuffer.charAt(offset - 1))) {
          int indent = calcIndent(offset);
          if (indent != -1 && indent <= myIndent) {
            return offset;
          }

          offset = findEol(offset);
        }
        else {
          offset++;
        }
      }

      return offset;
    }

    protected int calcIndent(int offset) {
      if (offset == myStartOffset) {
        return myIndent;
      }

      int answer = 0;
      offset--;
      while (offset >= myStartOffset) {
        final char c = myBuffer.charAt(offset);
        if (c == '\n') {
          return answer;
        }

        if (!StringUtil.isWhiteSpace(c)) {
          return -1;
        }

        answer++;
        offset--;
      }
      return answer + myIndent;
    }

    protected int findNonWhitespace(int offset) {
      while (offset < myEndOffset) {
        if (!StringUtil.isWhiteSpace(myBuffer.charAt(offset))) {
          return offset;
        }
        offset++;
      }
      return offset;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myEndOffset;
    }

    protected int findEol(int offset) {
      while (offset < myEndOffset) {
        if (myBuffer.charAt(offset) == '\n') {
          return offset;
        }
        offset++;
      }
      return offset;
    }
  }
}
