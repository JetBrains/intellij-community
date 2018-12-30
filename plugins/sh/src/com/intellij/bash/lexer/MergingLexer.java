package com.intellij.bash.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

class MergingLexer extends MergingLexerAdapterBase {
  private final LexerMergeFunction mergeFunction;

  /**
   * Create a merging lexer which works with the merge definitions given in the mergeTuples parameter.
   *
   * @param original    The original lexer, used as a delegate
   * @param mergeTuples The token merge definitions.
   */
  public MergingLexer(final Lexer original, final MergeTuple... mergeTuples) {
    super(original);
    this.mergeFunction = new LexerMergeFunction(mergeTuples);
  }

  @Override
  public MergeFunction getMergeFunction() {
    return mergeFunction;
  }

  private static class LexerMergeFunction implements MergeFunction {
    private final MergeTuple[] mergeTuples;

    public LexerMergeFunction(MergeTuple[] mergeTuples) {
      this.mergeTuples = mergeTuples;
    }

    @Override
    public IElementType merge(IElementType type, Lexer lexer) {
      for (MergeTuple currentTuple : mergeTuples) {
        TokenSet tokensToMerge = currentTuple.getTokensToMerge();

        if (tokensToMerge.contains(type)) {
          IElementType current = lexer.getTokenType();
          //merge all upcoming tokens into the target token type
          while (tokensToMerge.contains(current)) {
            lexer.advance();

            current = lexer.getTokenType();
          }

          return currentTuple.getTargetType();
        }
      }

      return type;
    }
  }
}
