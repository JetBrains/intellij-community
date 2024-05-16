package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;

public class PythonIndentingLexerForLazyElements extends PythonIndentingLexer {

  private final int myBaseIndent;

  public PythonIndentingLexerForLazyElements(int baseIndent) {
    super(PythonLexerKind.LAZY);
    myBaseIndent = baseIndent;
  }

  public void setStartStateForLazyReparse(int baseIndent) {
    myIndentStack.clear();
    myIndentStack.push(0);
    myIndentStack.push(baseIndent);
    myBraceLevel = 0;
    myLastNewLineIndent = baseIndent;
    myCurrentNewLineIndent = baseIndent;
    myLineHasSignificantTokens = false;
    checkSignificantTokens();
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int bsaeIndent) {
    checkStartState(startOffset, bsaeIndent);
    super.start(buffer, startOffset, endOffset, bsaeIndent);
    setStartStateForLazyReparse(myBaseIndent);
    if (myBaseIndent > 0) {
      myTokenQueue.add(0, new PendingToken(PyTokenTypes.INDENT, 0, 0));
    }
  }
}
