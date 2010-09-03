package com.jetbrains.python.lexer;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import gnu.trove.TIntStack;

/**
 * @author yole
 */
public class PythonIndentingLexer extends PythonFutureAwareLexer {
  private final TIntStack myIndentStack = new TIntStack();
  private int myBraceLevel;
  private boolean myLineHasSignificantTokens;

  private static final boolean DUMP_TOKENS = false;

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    checkStartState(startOffset, initialState);
    super.start(buffer, startOffset, endOffset, initialState);
    setStartState();
  }

  private static void checkStartState(int startOffset, int initialState) {
    if (DUMP_TOKENS) {
      System.out.println("\n--- LEXER START---");
    }
    if (startOffset != 0 || initialState != 0) {
      throw new RuntimeException("Indenting lexer does not support incremental lexing");
    }
  }

  private void setStartState() {
    myIndentStack.clear();
    myIndentStack.push(0);
    myBraceLevel = 0;
    adjustBraceLevel();
    myLineHasSignificantTokens = false;
    checkSignificantTokens();
  }

  private void adjustBraceLevel() {
    if (PyTokenTypes.OPEN_BRACES.contains(getTokenType())) {
      myBraceLevel++;
    }
    else if (PyTokenTypes.CLOSE_BRACES.contains(getTokenType())) {
      myBraceLevel--;
    }
  }

  private void checkSignificantTokens() {
    IElementType tokenType = getBaseTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != PyTokenTypes.END_OF_LINE_COMMENT) {
      myLineHasSignificantTokens = true;
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
          System.out.println("{" + myBraceLevel + "}");
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
      processInsignificantLineBreak(getBaseTokenStart(), true);
    }
    myProcessSpecialTokensPending = true;
  }

  private void processLineBreak(int startPos) {
    if (myBraceLevel == 0) {
      if (myLineHasSignificantTokens) {
        pushToken(PyTokenTypes.STATEMENT_BREAK, startPos, startPos);
      }
      myLineHasSignificantTokens = false;
      advanceBase();
      processIndent(startPos);
    }
    else {
      processInsignificantLineBreak(startPos, false);
    }
  }

  private void processInsignificantLineBreak(int startPos, boolean breakStatementOnLineBreak) {
    // merge whitespace following the line break character into the
    // line break token
    int end = getBaseTokenEnd();
    advanceBase();
    while (getBaseTokenType() == PyTokenTypes.SPACE || (!breakStatementOnLineBreak && getBaseTokenType() == PyTokenTypes.LINE_BREAK)) {
      end = getBaseTokenEnd();
      advanceBase();
    }
    if (breakStatementOnLineBreak && getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
      myTokenQueue.add(new PendingToken(PyTokenTypes.STATEMENT_BREAK, startPos, startPos));
      while (getBaseTokenType() == PyTokenTypes.SPACE || getBaseTokenType() == PyTokenTypes.LINE_BREAK) {
        end = getBaseTokenEnd();
        advanceBase();
      }
    }
    myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, startPos, end));
  }

  private void processIndent(int whiteSpaceStart) {
    int lastIndent = myIndentStack.peek();
    int indent = getNextLineIndent();
    // don't generate indent/dedent tokens if a line contains only end-of-line comment and whitespace
    if (getBaseTokenType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      indent = lastIndent;
    }
    int whiteSpaceEnd = (getBaseTokenType() == null) ? super.getBufferEnd() : getBaseTokenStart();
    if (indent > lastIndent) {
      myIndentStack.push(indent);
      myTokenQueue.add(new PendingToken(PyTokenTypes.LINE_BREAK, whiteSpaceStart, whiteSpaceEnd));
      myTokenQueue.add(new PendingToken(PyTokenTypes.INDENT, whiteSpaceEnd, whiteSpaceEnd));
    }
    else if (indent < lastIndent) {
      while (indent < lastIndent) {
        myIndentStack.pop();
        lastIndent = myIndentStack.peek();
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
