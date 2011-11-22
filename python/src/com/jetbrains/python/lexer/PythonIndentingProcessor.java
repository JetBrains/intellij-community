package com.jetbrains.python.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.FlexLexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PythonIndentingProcessor extends MergingLexerAdapter {
  protected final TIntStack myIndentStack = new TIntStack();
  protected int myBraceLevel;
  protected boolean myLineHasSignificantTokens;
  protected int myLastNewLineIndent = -1;

  private static final boolean DUMP_TOKENS = false;

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

    public PendingCommentToken(IElementType type, int start, int end, int indent) {
      super(type, start, end);
      myIndent = indent;
    }

    public int getIndent() {
      return myIndent;
    }
  }

  protected List<PendingToken> myTokenQueue = new ArrayList<PendingToken>();

  protected boolean myProcessSpecialTokensPending = false;

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
  }

  protected void pushToken(IElementType type, int start, int end) {
    myTokenQueue.add(new PendingToken(type, start, end));
  }

  @Override
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
    if (isBaseAt(PyTokenTypes.SPACE)) {
      processIndent(0, PyTokenTypes.SPACE);
    }
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
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType) && tokenType != getCommentTokenType()) {
      myLineHasSignificantTokens = true;
    }
  }

  protected void processSpecialTokens() {
    int tokenStart = getBaseTokenStart();
    if (isBaseAt(PyTokenTypes.LINE_BREAK)) {
      processLineBreak(tokenStart);
      while (isBaseAt(getCommentTokenType())) {
        // comment at start of line; maybe we need to generate dedent before the comments
        myTokenQueue.add(new PendingCommentToken(getBaseTokenType(), getBaseTokenStart(), getBaseTokenEnd(), myLastNewLineIndent));
        advanceBase();
        if (!isBaseAt(PyTokenTypes.LINE_BREAK)) {
          break;
        }
        processLineBreak(getBaseTokenStart());
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
    if (myBraceLevel == 0) {
      if (myLineHasSignificantTokens) {
        pushToken(PyTokenTypes.STATEMENT_BREAK, startPos, startPos);
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

  protected void processIndent(int whiteSpaceStart, IElementType whitespaceTokenType) {
    int lastIndent = myIndentStack.peek();
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
      while (indent < lastIndent) {
        myIndentStack.pop();
        lastIndent = myIndentStack.peek();
        int insertIndex = myTokenQueue.size();
        int dedentOffset = whiteSpaceStart;
        if (indent > lastIndent) {
          myTokenQueue.add(new PendingToken(PyTokenTypes.INCONSISTENT_DEDENT, whiteSpaceStart, whiteSpaceStart));
          insertIndex++;
        }
        else {
          insertIndex = skipPrecedingCommentsWithIndent(indent, insertIndex);
        }
        if (insertIndex != myTokenQueue.size()) {
          dedentOffset = myTokenQueue.get(insertIndex).getStart();
        }
        myTokenQueue.add(insertIndex, new PendingToken(PyTokenTypes.DEDENT, dedentOffset, dedentOffset));
      }
      myTokenQueue.add(new PendingToken(whitespaceTokenType, whiteSpaceStart, whiteSpaceEnd));
    }
    else {
      myTokenQueue.add(new PendingToken(whitespaceTokenType, whiteSpaceStart, whiteSpaceEnd));
    }
  }

  protected int skipPrecedingCommentsWithIndent(int indent, int index) {
    // insert the DEDENT before previous comments that have the same indent as the current token indent
    boolean foundComment = false;
    while(index > 0 && myTokenQueue.get(index-1) instanceof PendingCommentToken) {
      final PendingCommentToken commentToken = (PendingCommentToken)myTokenQueue.get(index - 1);
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
