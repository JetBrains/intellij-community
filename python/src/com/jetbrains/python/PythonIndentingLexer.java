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
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author yole
 */
public class PythonIndentingLexer extends PythonLexer {
  private static class PendingToken {
    private IElementType _type;
    private int _start;
    private int _end;

    public PendingToken(IElementType type, int start, int end) {
      _type = type;
      _start = start;
      _end = end;
    }

    public IElementType getType() {
      return _type;
    }

    public int getStart() {
      return _start;
    }

    public int getEnd() {
      return _end;
    }

    public void setType(IElementType type) {
      _type = type;
    }
  }

  private Stack<Integer> _indentStack = new Stack<Integer>();
  private int _braceLevel;
  private boolean _lineHasSignificantTokens;
  private List<PendingToken> _tokenQueue = new ArrayList<PendingToken>();

  // Limited size list of previously processed tokens used for 'from __future__ import ...' processing
  private List<PendingToken> myTokenHistory = new ArrayList<PendingToken>();

  private boolean myFutureWithStatement;

  private static final boolean DUMP_TOKENS = false;

  @NonNls private static final String FUTURE_IMPORT = "__future__";
  @NonNls private static final String FUTURE_WITH_STATEMENT = "with_statement";
  @NonNls private static final String WITH_KEYWORD = "with";
  @NonNls private static final String AS_KEYWORD = "as";

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

  @Override
  public IElementType getTokenType() {
    if (_tokenQueue.size() > 0) {
      return _tokenQueue.get(0).getType();
    }
    return super.getTokenType();
  }

  @Override
  public int getTokenStart() {
    if (_tokenQueue.size() > 0) {
      return _tokenQueue.get(0).getStart();
    }
    return super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    if (_tokenQueue.size() > 0) {
      return _tokenQueue.get(0).getEnd();
    }
    return super.getTokenEnd();
  }

  private void advanceBase() {
    super.advance();
    checkSignificantTokens();
  }

  private void checkSignificantTokens() {
    IElementType tokenType = super.getTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != PyTokenTypes.END_OF_LINE_COMMENT) {
      _lineHasSignificantTokens = true;
    }
  }

  @Override
  public void advance() {
    IElementType tokenType = getTokenType();
    if (tokenType == PyTokenTypes.FROM_KEYWORD || myTokenHistory.size() > 0) {
      putTokenInHistory();
    }
    else if (tokenType == PyTokenTypes.LINE_BREAK) {
      myTokenHistory.clear();
    }
    if (_tokenQueue.size() > 0) {
      _tokenQueue.remove(0);
    }
    else {
      advanceBase();
      int tokenStart = super.getTokenStart();
      if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
        processLineBreak(tokenStart);
      }
      else if (super.getTokenType() == PyTokenTypes.BACKSLASH) {
        processBackslash(tokenStart);
      }
      else if (super.getTokenType() == PyTokenTypes.SPACE) {
        processSpace();
      }
      if (myFutureWithStatement && super.getTokenType() == PyTokenTypes.IDENTIFIER) {
        if (CharArrayUtil.regionMatches(getBufferSequence(), super.getTokenStart(), WITH_KEYWORD)) {
          pushToken(PyTokenTypes.WITH_KEYWORD, super.getTokenStart(), super.getTokenEnd());
          advanceBase();
        }
        else if (CharArrayUtil.regionMatches(getBufferSequence(), super.getTokenStart(), AS_KEYWORD)) {
          pushToken(PyTokenTypes.AS_KEYWORD, super.getTokenStart(), super.getTokenEnd());
          advanceBase();
        }
      }
    }

    adjustBraceLevel();
    checkImportFromFuture();
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

  private void checkImportFromFuture() {
    if (getTokenType() == PyTokenTypes.IDENTIFIER &&
        myTokenHistory.size() >= 3) {
      IElementType importCandidate = myTokenHistory.get(myTokenHistory.size()-1)._type;
      if (importCandidate != PyTokenTypes.IMPORT_KEYWORD) return;
      PendingToken fromCandidate = myTokenHistory.get(myTokenHistory.size()-3);
      if (fromCandidate._type != PyTokenTypes.FROM_KEYWORD) return;

      if (!CharArrayUtil.regionMatches(getBufferSequence(), getTokenStart(), FUTURE_WITH_STATEMENT)) return;
      PendingToken futureCandidate = myTokenHistory.get(myTokenHistory.size()-2);
      if (futureCandidate._type != PyTokenTypes.IDENTIFIER ||
          !CharArrayUtil.regionMatches(getBufferSequence(), futureCandidate._start, FUTURE_IMPORT)) {
        return;
      }
      myFutureWithStatement = true;
    }
  }

  private void putTokenInHistory() {
    if (myTokenHistory.size() > 5) {
      myTokenHistory.remove(0);
    }
    IElementType tokenType = getTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType)) {
      myTokenHistory.add(new PendingToken(tokenType, getTokenStart(), getTokenEnd()));
    }
  }

  private void processSpace() {
    int start = super.getTokenStart();
    int end = super.getTokenEnd();
    while (super.getTokenType() == PyTokenTypes.SPACE) {
      end = super.getTokenEnd();
      advanceBase();
    }
    if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
      processLineBreak(start);
    }
    else if (super.getTokenType() == PyTokenTypes.BACKSLASH) {
      processBackslash(start);
    }
    else {
      _tokenQueue.add(new PendingToken(PyTokenTypes.SPACE, start, end));
    }
  }

  private void processBackslash(int tokenStart) {
    PendingToken backslashToken = new PendingToken(super.getTokenType(), tokenStart, super.getTokenEnd());
    _tokenQueue.add(backslashToken);
    advanceBase();
    while (PyTokenTypes.WHITESPACE.contains(super.getTokenType())) {
      pushCurrentToken();
      advanceBase();
    }
    if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
      backslashToken.setType(PyTokenTypes.SPACE);
      processInsignificantLineBreak(super.getTokenStart());
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
    int end = super.getTokenEnd();
    advanceBase();
    while (super.getTokenType() == PyTokenTypes.SPACE || super.getTokenType() == PyTokenTypes.LINE_BREAK) {
      end = super.getTokenEnd();
      advanceBase();
    }
    _tokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, startPos, end));
  }

  private void processIndent(int whiteSpaceStart) {
    int lastIndent = _indentStack.peek();
    int indent = getNextLineIndent();
    // don't generate indent/dedent tokens if a line contains only end-of-line comment and whitespace
    if (super.getTokenType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      indent = lastIndent;
    }
    int whiteSpaceEnd = (super.getTokenType() == null) ? super.getBufferEnd() : super.getTokenStart();
    if (indent > lastIndent) {
      _indentStack.push(indent);
      _tokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
      _tokenQueue.add(new PendingToken(PyTokenTypes.INDENT, whiteSpaceEnd, whiteSpaceEnd));
    }
    else if (indent < lastIndent) {
      while (indent < lastIndent) {
        _indentStack.pop();
        lastIndent = _indentStack.peek();
        if (indent > lastIndent) {
          _tokenQueue.add(new PendingToken(PyTokenTypes.INCONSISTENT_DEDENT, whiteSpaceStart, whiteSpaceStart));
        }
        _tokenQueue.add(new PendingToken(PyTokenTypes.DEDENT, whiteSpaceStart, whiteSpaceStart));
      }
      _tokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
    }
    else {
      _tokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
    }
  }

  private int getNextLineIndent() {
    int indent = 0;
    while (super.getTokenType() != null && PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(super.getTokenType())) {
      if (super.getTokenType() == PyTokenTypes.TAB) {
        indent = ((indent / 8) + 1) * 8;
      }
      else if (super.getTokenType() == PyTokenTypes.SPACE) {
        indent++;
      }
      else if (super.getTokenType() == PyTokenTypes.LINE_BREAK) {
        indent = 0;
      }
      advanceBase();
    }
    if (super.getTokenType() == null) {
      return 0;
    }
    return indent;
  }

  private void pushCurrentToken() {
    _tokenQueue.add(new PendingToken(super.getTokenType(), super.getTokenStart(), super.getTokenEnd()));
  }

  private void pushToken(IElementType type, int start, int end) {
    _tokenQueue.add(new PendingToken(type, start, end));
  }
}