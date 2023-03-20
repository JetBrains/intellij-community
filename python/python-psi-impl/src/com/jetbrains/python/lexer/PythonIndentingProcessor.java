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
package com.jetbrains.python.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.FlexLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PythonIndentingProcessor extends MergingLexerAdapter {
  @SuppressWarnings("SSBasedInspection")
  protected final IntArrayList myIndentStack = new IntArrayList();
  protected int myBraceLevel;
  protected boolean myLineHasSignificantTokens;
  protected int myLastNewLineIndent = -1;
  private int myCurrentNewLineIndent = 0;

  protected List<PendingToken> myTokenQueue = new ArrayList<>();
  private int myLineBreakBeforeFirstCommentIndex = -1;
  protected boolean myProcessSpecialTokensPending = false;

  private final Stack<String> myFStringStack = new Stack<>();

  private static final boolean DUMP_TOKENS = false;
  private final TokenSet RECOVERY_TOKENS = PythonDialectsTokenSetProvider.getInstance().getUnbalancedBracesRecoveryTokens();

  public PythonIndentingProcessor(FlexLexer lexer, TokenSet tokens) {
    super(new FlexAdapter(lexer), tokens);
  }

  protected static class PendingToken {
    private IElementType _type;
    private final int _start;
    private final int _end;

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

    @Override
    public String toString() {
      return _type + ":" + _start + "-" + _end;
    }
  }

  private static class PendingCommentToken extends PendingToken {
    private final int myIndent;

    PendingCommentToken(IElementType type, int start, int end, int indent) {
      super(type, start, end);
      myIndent = indent;
    }

    public int getIndent() {
      return myIndent;
    }
  }

  @Nullable
  protected IElementType getBaseTokenType() {
    return super.getTokenType();
  }

  protected int getBaseTokenStart() {
    return super.getTokenStart();
  }

  protected int getBaseTokenEnd() {
    return super.getTokenEnd();
  }

  @NotNull
  private String getBaseTokenText() {
    return getBufferSequence().subSequence(getBaseTokenStart(), getBaseTokenEnd()).toString();
  }

  private boolean isBaseAt(IElementType tokenType) {
    return getBaseTokenType() == tokenType;
  }

  @Override
  public IElementType getTokenType() {
    if (myTokenQueue.size() > 0) {
      return myTokenQueue.get(0).getType();
    }
    return super.getTokenType();
  }

  @Override
  public int getTokenStart() {
    if (myTokenQueue.size() > 0) {
      return myTokenQueue.get(0).getStart();
    }
    return super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    if (myTokenQueue.size() > 0) {
      return myTokenQueue.get(0).getEnd();
    }
    return super.getTokenEnd();
  }

  @Override
  public void advance() {
    if (getTokenType() == PyTokenTypes.LINE_BREAK) {
      final String text = getTokenText();
      int spaces = 0;
      for (int i = text.length() - 1; i >= 0; i--) {
        if (text.charAt(i) == ' ') {
          spaces++;
        }
        else if (text.charAt(i) == '\t') {
          spaces += 8;
        }
      }
      myCurrentNewLineIndent = spaces;
    }
    else if (getTokenType() == PyTokenTypes.TAB) {
      myCurrentNewLineIndent += 8;
    }
    if (myTokenQueue.size() > 0) {
      myTokenQueue.remove(0);
      if (myProcessSpecialTokensPending) {
        myProcessSpecialTokensPending = false;
        processSpecialTokens();
      }
    }
    else {
      advanceBase();
      processSpecialTokens();
    }
    adjustBraceLevel();
    if (DUMP_TOKENS) {
      if (getTokenType() != null) {
        System.out.print(getTokenStart() + "-" + getTokenEnd() + ":" + getTokenType());
        if (getTokenType() == PyTokenTypes.LINE_BREAK) {
          System.out.println("{" + myBraceLevel + "}");
        }
        else {
          System.out.print(" ");
        }
      }
    }
  }

  protected void advanceBase() {
    super.advance();
    checkSignificantTokens();
    checkFString();
  }

  private void checkFString() {
    final String tokenText = getBaseTokenText();
    if (isBaseAt(PyTokenTypes.FSTRING_START)) {
      final int prefixLength = PyStringLiteralUtil.getPrefixLength(tokenText);
      final String openingQuotes = tokenText.substring(prefixLength);
      assert !openingQuotes.isEmpty();
      myFStringStack.push(openingQuotes);
    }
    else if (isBaseAt(PyTokenTypes.FSTRING_END)) {
      while (!myFStringStack.isEmpty()) {
        final String lastOpeningQuotes = myFStringStack.pop();
        if (lastOpeningQuotes.equals(tokenText)) {
          break;
        }
      }
    }
  }

  protected void pushToken(IElementType type, int start, int end) {
    myTokenQueue.add(new PendingToken(type, start, end));
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    checkStartState(startOffset, initialState);
    super.start(buffer, startOffset, endOffset, initialState);
    setStartState();
  }

  protected void checkStartState(int startOffset, int initialState) {
    if (DUMP_TOKENS) {
      System.out.println("\n--- LEXER START---");
    }
  }

  private void setStartState() {
    myIndentStack.clear();
    myIndentStack.push(0);
    myBraceLevel = 0;
    adjustBraceLevel();
    myLineHasSignificantTokens = false;
    checkSignificantTokens();
    checkFString();
    if (isBaseAt(PyTokenTypes.SPACE)) {
      processIndent(0, PyTokenTypes.SPACE);
    }
  }

  private void adjustBraceLevel() {
    final IElementType tokenType = getTokenType();
    if (PyTokenTypes.OPEN_BRACES.contains(tokenType)) {
      myBraceLevel++;
    }
    else if (PyTokenTypes.CLOSE_BRACES.contains(tokenType)) {
      myBraceLevel--;
    }
    else if (myBraceLevel != 0 && RECOVERY_TOKENS.contains(tokenType)) {
      myBraceLevel = 0;
      final int pos = getTokenStart();
      pushToken(PyTokenTypes.STATEMENT_BREAK, pos, pos);
      final int indents = myIndentStack.size();
      for (int i = 0; i < indents - 1; i++) {
        final int indent = myIndentStack.topInt();
        if (myCurrentNewLineIndent >= indent) {
          break;
        }
        if (myIndentStack.size() > 1) {
          myIndentStack.pop();
          pushToken(PyTokenTypes.DEDENT, pos, pos);
        }
      }
      pushToken(PyTokenTypes.LINE_BREAK, pos, pos);
    }
  }

  protected void checkSignificantTokens() {
    IElementType tokenType = getBaseTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != getCommentTokenType()) {
      myLineHasSignificantTokens = true;
    }
  }

  protected void processSpecialTokens() {
    int tokenStart = getBaseTokenStart();
    if (isBaseAt(PyTokenTypes.LINE_BREAK)) {
      processLineBreak(tokenStart);
      if (isBaseAt(getCommentTokenType())) {
        myLineBreakBeforeFirstCommentIndex = myTokenQueue.size() - 1;
        while (isBaseAt(getCommentTokenType())) {
          // comment at start of line; maybe we need to generate dedent before the comments
          final int commentEnd = getBaseTokenEnd();
          myTokenQueue.add(new PendingCommentToken(getBaseTokenType(), getBaseTokenStart(), commentEnd, myLastNewLineIndent));
          advanceBase();
          if (isBaseAt(PyTokenTypes.LINE_BREAK)) {
            processLineBreak(getBaseTokenStart());
          }
          // Treat EOF as an indent of size 0
          else if (getBaseTokenType() == null) {
            closeDanglingSuitesWithComments(0, commentEnd);
          }
          else {
            break;
          }
        }
        myLineBreakBeforeFirstCommentIndex = -1;
      }
    }
    else if (isBaseAt(PyTokenTypes.BACKSLASH)) {
      processBackslash(tokenStart);
    }
    else if (isBaseAt(PyTokenTypes.SPACE)) {
      processSpace();
    }
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
      processInsignificantLineBreak(getBaseTokenStart(), true);
    }
    myProcessSpecialTokensPending = true;
  }

  protected void processLineBreak(int startPos) {
    // See https://www.python.org/dev/peps/pep-0498/#expression-evaluation
    final boolean allFStringsAreTripleQuoted = ContainerUtil.and(myFStringStack, quotes -> quotes.length() == 3);
    final boolean insideImplicitFragmentParentheses = !myFStringStack.isEmpty() && allFStringsAreTripleQuoted;
    final boolean shouldTerminateFStrings = !myFStringStack.isEmpty() && !allFStringsAreTripleQuoted;
    if ((myBraceLevel == 0 && !insideImplicitFragmentParentheses) || shouldTerminateFStrings) {
      if (myLineHasSignificantTokens || shouldTerminateFStrings) {
        pushToken(PyTokenTypes.STATEMENT_BREAK, startPos, startPos);
        myFStringStack.clear();
      }
      myLineHasSignificantTokens = false;
      advanceBase();
      processIndent(startPos, PyTokenTypes.LINE_BREAK);
    }
    else {
      processInsignificantLineBreak(startPos, false);
    }
  }

  protected void processInsignificantLineBreak(int startPos,
                                               boolean breakStatementOnLineBreak) {
    // merge whitespace following the line break character into the
    // line break token
    int end = getBaseTokenEnd();
    advanceBase();
    while (getBaseTokenType() == PyTokenTypes.SPACE || getBaseTokenType() == PyTokenTypes.TAB ||
           (!breakStatementOnLineBreak && getBaseTokenType() == PyTokenTypes.LINE_BREAK)) {
      end = getBaseTokenEnd();
      advanceBase();
    }
    myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, startPos, end));
    myProcessSpecialTokensPending = true;
  }

  protected void processIndent(int whiteSpaceStart, IElementType whitespaceTokenType) {
    int lastIndent = myIndentStack.topInt();
    int indent = getNextLineIndent();
    myLastNewLineIndent = indent;
    // don't generate indent/dedent tokens if a line contains only end-of-line comment and whitespace
    if (getBaseTokenType() == getCommentTokenType()) {
      indent = lastIndent;
    }
    int whiteSpaceEnd = (getBaseTokenType() == null) ? super.getBufferEnd() : getBaseTokenStart();
    if (indent > lastIndent) {
      myIndentStack.push(indent);
      myTokenQueue.add(new PendingToken(whitespaceTokenType, whiteSpaceStart, whiteSpaceEnd));
      int insertIndex = skipPrecedingCommentsWithIndent(indent, myTokenQueue.size() - 1);
      int indentOffset = insertIndex == myTokenQueue.size() ? whiteSpaceEnd : myTokenQueue.get(insertIndex).getStart();
      myTokenQueue.add(insertIndex, new PendingToken(PyTokenTypes.INDENT, indentOffset, indentOffset));
    }
    else if (indent < lastIndent) {
      closeDanglingSuitesWithComments(indent, whiteSpaceStart);
      myTokenQueue.add(new PendingToken(whitespaceTokenType, whiteSpaceStart, whiteSpaceEnd));
    }
    else {
      myTokenQueue.add(new PendingToken(whitespaceTokenType, whiteSpaceStart, whiteSpaceEnd));
    }
  }

  private void closeDanglingSuitesWithComments(int indent, int whiteSpaceStart) {
    int lastIndent = myIndentStack.topInt();

    int insertIndex = myLineBreakBeforeFirstCommentIndex == -1 ? myTokenQueue.size() : myLineBreakBeforeFirstCommentIndex;
    int lastSuiteIndent;
    while (indent < lastIndent) {
      lastSuiteIndent = myIndentStack.popInt();
      lastIndent = myIndentStack.topInt();
      int dedentOffset = whiteSpaceStart;
      if (indent > lastIndent) {
        myTokenQueue.add(new PendingToken(PyTokenTypes.INCONSISTENT_DEDENT, whiteSpaceStart, whiteSpaceStart));
        insertIndex = myTokenQueue.size();
      }
      else {
        insertIndex = skipPrecedingCommentsWithSameIndentOnSuiteClose(lastSuiteIndent, insertIndex);
      }
      if (insertIndex != myTokenQueue.size()) {
        dedentOffset = myTokenQueue.get(insertIndex).getStart();
      }
      myTokenQueue.add(insertIndex, new PendingToken(PyTokenTypes.DEDENT, dedentOffset, dedentOffset));
      insertIndex++;
    }
  }

  protected int skipPrecedingCommentsWithIndent(int indent, int index) {
    // insert the DEDENT before previous comments that have the same indent as the current token indent
    boolean foundComment = false;
    while(index > 0 && myTokenQueue.get(index - 1) instanceof PendingCommentToken commentToken) {
      if (commentToken.getIndent() != indent) {
        break;
      }
      foundComment = true;
      index--;
      if (index > 1 &&
          myTokenQueue.get(index - 1).getType() == PyTokenTypes.LINE_BREAK &&
          myTokenQueue.get(index - 2) instanceof PendingCommentToken) {
        index--;
      }
    }
    return foundComment ? index : myTokenQueue.size();
  }

  protected int skipPrecedingCommentsWithSameIndentOnSuiteClose(int indent, int anchorIndex) {
    int result = anchorIndex;
    for (int i = anchorIndex; i < myTokenQueue.size(); i++) {
      final PendingToken token = myTokenQueue.get(i);
      if (token instanceof PendingCommentToken) {
        if (((PendingCommentToken)token).getIndent() < indent) {
          break;
        }
        result = i + 1;
      }
    }
    return result;
  }

  protected int getNextLineIndent() {
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


  protected IElementType getCommentTokenType() {
    return PyTokenTypes.END_OF_LINE_COMMENT;
  }
}
