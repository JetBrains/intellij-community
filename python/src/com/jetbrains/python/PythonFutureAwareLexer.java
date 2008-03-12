package com.jetbrains.python;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonFutureAwareLexer extends PythonLexer {// Limited size list of previously processed tokens used for 'from __future__ import ...' processing
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
  protected List<PendingToken> myTokenHistory = new ArrayList<PendingToken>();
  protected boolean myFutureWithStatement;
  @NonNls private static final String FUTURE_IMPORT = "__future__";
  @NonNls private static final String FUTURE_WITH_STATEMENT = "with_statement";
  @NonNls protected static final String WITH_KEYWORD = "with";
  @NonNls protected static final String AS_KEYWORD = "as";

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

    checkImportFromFuture();
  }

  protected void processSpecialTokens() {
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


  protected void advanceBase() {
    super.advance();
  }

  protected void checkImportFromFuture() {
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
