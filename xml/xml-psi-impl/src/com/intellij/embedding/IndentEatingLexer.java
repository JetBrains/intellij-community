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

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  This masquerading lexer cuts out `myIndent` spaces/tabs after each newline in the text passed to this lexer.
 *  The string without these parts (indents) is then passed to a delegate lexer to lex correctly without these indents.
 *  The production of this lexer can also be effectively used in {@link com.intellij.embedding.MasqueradingPsiBuilderAdapter}
 *  to parse the text without these indents.
 */
public class IndentEatingLexer extends MasqueradingLexer.SmartDelegate {
  private final int myIndent;
  @NotNull
  private CharSequence myBuffer;
  @NotNull
  private List<DeletedIndentInfo> myDeletions;

  private int myCurrentDelta;
  private int myTotalDelta;
  private int myCurrentDelIndex;

  public IndentEatingLexer(@NotNull Lexer delegate, int baseIndent) {
    super(delegate);
    myIndent = baseIndent;
    myBuffer = "";
    myDeletions = Collections.emptyList();
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myDeletions = findAllDeletions(buffer, startOffset, endOffset);

    myCurrentDelta = 0;
    myTotalDelta = 0;
    if (myDeletions.isEmpty()) {
      super.start(buffer, startOffset, endOffset, initialState);
      return;
    }
    CharSequence newSequence = deleteIndents(buffer, startOffset, endOffset);

    super.start(newSequence, 0, newSequence.length(), initialState);

    myTotalDelta = startOffset;
    myCurrentDelIndex = 0;
    updateDeltas();
  }

  private void updateDeltas() {
    myTotalDelta += myCurrentDelta;
    myCurrentDelta = 0;
    while (myCurrentDelIndex < myDeletions.size()) {
      final DeletedIndentInfo info = myDeletions.get(myCurrentDelIndex);
      if (info.getShrunkPos() >= super.getTokenStart()
        && info.getShrunkPos() <= super.getTokenEnd()) {
        myCurrentDelta += info.getLength();
        myCurrentDelIndex++;
      }
      else {
        break;
      }
    }
  }

  @Override
  public void advance() {
    super.advance();
    updateDeltas();
  }

  @Override
  public int getTokenStart() {
    return super.getTokenStart() + myTotalDelta;
  }

  @Override
  public int getTokenEnd() {
    return super.getTokenEnd() + myTotalDelta + myCurrentDelta;
  }

  @NotNull
  @Override
  public String getTokenText() {
    return myBuffer.subSequence(getTokenStart(), getTokenEnd()).toString();
  }

  @NotNull
  private List<DeletedIndentInfo> findAllDeletions(@NotNull CharSequence buffer, int startOffset, int endOffset) {
    List<DeletedIndentInfo> result = new ArrayList<>();

    int offset = startOffset;
    while (offset < endOffset) {
      final int newline = StringUtil.indexOf(buffer, '\n', offset, endOffset);
      if (newline < 0) {
        break;
      }
      int charsToDelete = 0;

      char c;
      while (charsToDelete < myIndent
          && newline + charsToDelete + 1 < endOffset
          && (c = buffer.charAt(newline + charsToDelete + 1)) != '\n'
          && Character.isWhitespace(c)) {
        charsToDelete++;
      }

      if (charsToDelete > 0) {
        result.add(new DeletedIndentInfo(newline + 1, charsToDelete));
      }
      offset = newline + charsToDelete + 1;
    }

    return result;
  }

  @NotNull
  private CharSequence deleteIndents(@NotNull CharSequence buffer, int startOffset, int endOffset) {
    StringBuilder result = new StringBuilder();

    int offset = startOffset;
    for (DeletedIndentInfo deletion : myDeletions) {
      result.append(buffer.subSequence(offset, deletion.getRealPos()));
      deletion.setShrunkPos(result.length());
      offset = deletion.getRealPos() + deletion.getLength();
    }
    result.append(buffer.subSequence(offset, endOffset));

    return result.toString();
  }

  private static class DeletedIndentInfo {
    private final int realPos;
    private final int length;
    private int shrunkPos;

    public DeletedIndentInfo(int realPos, int length) {
      this.realPos = realPos;
      this.length = length;
      shrunkPos = -1;
    }

    public void setShrunkPos(int shrunkPos) {
      this.shrunkPos = shrunkPos;
    }

    public int getRealPos() {
      return realPos;
    }

    public int getLength() {
      return length;
    }

    public int getShrunkPos() {
      return shrunkPos;
    }
  }
}
