package com.jetbrains.python.lexer;

import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;

public class PythonIndentingLexerForLazyElements extends PythonIndentingLexer {

  private final int myBaseIndent;

  public PythonIndentingLexerForLazyElements(int baseIndent) {
    super(PythonLexerKind.LAZY);
    myBaseIndent = baseIndent;
  }

  /**
   * Initializes the indent stack as if we are already inside a block indented to {@code baseIndent}.
   * <p>
   * Stack {@code [0, baseIndent]} means: module level (0) → current block (baseIndent).
   * This lets the lexer generate correct INDENT/DEDENT tokens for content within the block
   * without needing the surrounding file context.
   */
  private void setStartStateForLazyReparse(int baseIndent) {
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
      myTokenQueue.addFirst(new PendingToken(PyTokenTypes.INDENT, 0, 0));
    }
  }
}
