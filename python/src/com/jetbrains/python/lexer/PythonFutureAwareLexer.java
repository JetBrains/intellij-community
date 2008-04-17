package com.jetbrains.python.lexer;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonFutureAwareLexer extends PythonLexer {
  protected static class PendingToken {
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

  protected List<PendingToken> myTokenQueue = new ArrayList<PendingToken>();

  // Limited size list of previously processed tokens used for 'from __future__ import ...' processing
  protected List<PendingToken> myTokenHistory = new ArrayList<PendingToken>();

  protected IElementType getBaseTokenType() {
    return super.getTokenType();
  }

  protected int getBaseTokenStart() {
    return super.getTokenStart();
  }

  protected int getBaseTokenEnd() {
    return super.getTokenEnd();
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
    IElementType tokenType = getTokenType();
    if (tokenType == PyTokenTypes.FROM_KEYWORD || myTokenHistory.size() > 0) {
      putTokenInHistory();
    }
    else if (tokenType == PyTokenTypes.LINE_BREAK) {
      myTokenHistory.clear();
    }
    if (myTokenQueue.size() > 0) {
      myTokenQueue.remove(0);
    }
    else {
      advanceBase();
      processSpecialTokens();
    }

  }

  protected void processSpecialTokens() {
    // NOTE: inheritors will override this
  }


  protected void advanceBase() {
    super.advance();
  }

  protected void putTokenInHistory() {
    if (myTokenHistory.size() > 5) {
      myTokenHistory.remove(0);
    }
    IElementType tokenType = getTokenType();
    if (!PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(tokenType)) {
      myTokenHistory.add(new PendingToken(tokenType, getTokenStart(), getTokenEnd()));
    }
  }

  protected void pushToken(IElementType type, int start, int end) {
    myTokenQueue.add(new PendingToken(type, start, end));
  }

}
