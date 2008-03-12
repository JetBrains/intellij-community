/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.psi.tree.IElementType;

import java.util.Stack;

/**
 * @author yole
 */
public class PythonIndentingLexer extends PythonFutureAwareLexer {
  private Stack<Integer> _indentStack = new Stack<Integer>();
  private int _braceLevel;
  private boolean _lineHasSignificantTokens;

  private static final boolean DUMP_TOKENS = false;

  // used in Demetra and earlier
  @Override
  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    checkStartState(startOffset, initialState);
    super.start(buffer, startOffset, endOffset, initialState);
    setStartState();
  }

  // used in Selena
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    checkStartState(startOffset, initialState);
    super.start(buffer, startOffset, endOffset, initialState);
    setStartState();
  }

  private void checkStartState(int startOffset, int initialState) {
    if (DUMP_TOKENS) {
      System.out.println("\n--- LEXER START---");
    }
    if (startOffset != 0 || initialState != 0) {
      throw new RuntimeException("Indenting lexer does not support incremental lexing");
    }
  }

  private void setStartState() {
    _indentStack.clear();
    _indentStack.push(0);
    _braceLevel = 0;
    adjustBraceLevel();
    _lineHasSignificantTokens = false;
    checkSignificantTokens();
  }

  private void adjustBraceLevel() {
    if (PyTokenTypes.OPEN_BRACES.contains(getTokenType())) {
      _braceLevel++;
    }
    else if (PyTokenTypes.CLOSE_BRACES.contains(getTokenType())) {
      _braceLevel--;
    }
  }

  private void checkSignificantTokens() {
    IElementType tokenType = getBaseTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != PyTokenTypes.END_OF_LINE_COMMENT) {
      _lineHasSignificantTokens = true;
    }
  }

  @Override
  protected void advanceBase() {
    super.advanceBase();
    checkSignificantTokens();
  }

  @Override
  public void advance() {
    super.advance();
    adjustBraceLevel();
    if (DUMP_TOKENS) {
      if (getTokenType() != null) {
        System.out.print(getTokenStart() + "-" + getTokenEnd() + ":" + getTokenType());
        if (getTokenType() == PyTokenTypes.LINE_BREAK) {
          System.out.println("{" + _braceLevel + "}");
        }
        else {
          System.out.print(" ");
        }
      }
    }
  }

  protected void processSpecialTokens() {
    int tokenStart = getBaseTokenStart();
    if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
      processLineBreak(tokenStart);
    }
    else if (getBaseTokenType() == PyTokenTypes.BACKSLASH) {
      processBackslash(tokenStart);
    }
    else if (getBaseTokenType() == PyTokenTypes.SPACE) {
      processSpace();
    }
    super.processSpecialTokens();
  }

  private void processSpace() {
    int start = getBaseTokenStart();
    int end = getBaseTokenEnd();
    while (getBaseTokenType() == PyTokenTypes.SPACE) {
      end = getBaseTokenEnd();
      advanceBase();
    }
    if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
      processLineBreak(start);
    }
    else if (getBaseTokenType() == PyTokenTypes.BACKSLASH) {
      processBackslash(start);
    }
    else {
      myTokenQueue.add(new PendingToken(PyTokenTypes.SPACE, start, end));
    }
  }

  private void processBackslash(int tokenStart) {
    PendingToken backslashToken = new PendingToken(getBaseTokenType(), tokenStart, getBaseTokenEnd());
    myTokenQueue.add(backslashToken);
    advanceBase();
    while (PyTokenTypes.WHITESPACE.contains(getBaseTokenType())) {
      pushCurrentToken();
      advanceBase();
    }
    if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
      backslashToken.setType(PyTokenTypes.SPACE);
      processInsignificantLineBreak(getBaseTokenStart());
    }
  }

  private void processLineBreak(int startPos) {
    if (_braceLevel == 0) {
      if (_lineHasSignificantTokens) {
        pushToken(PyTokenTypes.STATEMENT_BREAK, startPos, startPos);
      }
      _lineHasSignificantTokens = false;
      advanceBase();
      processIndent(startPos);
    }
    else {
      processInsignificantLineBreak(startPos);
    }
  }

  private void processInsignificantLineBreak(int startPos) {
    // merge whitespace following the line break character into the
    // line break token
    int end = getBaseTokenEnd();
    advanceBase();
    while (getBaseTokenType() == PyTokenTypes.SPACE || getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
      end = getBaseTokenEnd();
      advanceBase();
    }
    myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, startPos, end));
  }

  private void processIndent(int whiteSpaceStart) {
    int lastIndent = _indentStack.peek();
    int indent = getNextLineIndent();
    // don't generate indent/dedent tokens if a line contains only end-of-line comment and whitespace
    if (getBaseTokenType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      indent = lastIndent;
    }
    int whiteSpaceEnd = (getBaseTokenType() == null) ? super.getBufferEnd() : getBaseTokenStart();
    if (indent > lastIndent) {
      _indentStack.push(indent);
      myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
      myTokenQueue.add(new PendingToken(PyTokenTypes.INDENT, whiteSpaceEnd, whiteSpaceEnd));
    }
    else if (indent < lastIndent) {
      while (indent < lastIndent) {
        _indentStack.pop();
        lastIndent = _indentStack.peek();
        if (indent > lastIndent) {
          myTokenQueue.add(new PendingToken(PyTokenTypes.INCONSISTENT_DEDENT, whiteSpaceStart, whiteSpaceStart));
        }
        myTokenQueue.add(new PendingToken(PyTokenTypes.DEDENT, whiteSpaceStart, whiteSpaceStart));
      }
      myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
    }
    else {
      myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
    }
  }

  private int getNextLineIndent() {
    int indent = 0;
    while (getBaseTokenType() != null && PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(getBaseTokenType())) {
      if (getBaseTokenType() == PyTokenTypes.TAB) {
        indent = ((indent / 8) + 1) * 8;
      }
      else if (getBaseTokenType() == PyTokenTypes.SPACE) {
        indent++;
      }
      else if (getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
        indent = 0;
      }
      advanceBase();
    }
    if (getBaseTokenType() == null) {
      return 0;
    }
    return indent;
  }

  private void pushCurrentToken() {
    myTokenQueue.add(new PendingToken(getBaseTokenType(), getBaseTokenStart(), getBaseTokenEnd()));
  }

}