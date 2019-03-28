package com.intellij.bash.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class BashLexer extends MergingLexerAdapterBase implements BashTokenTypes {
  private static final MergeTuple[] TUPLES = {MergeTuple.create(TokenSet.create(STRING_DATA), STRING_CONTENT), MergeTuple.create(TokenSet.create(HEREDOC_LINE), HEREDOC_CONTENT)};
  private static final MergeFunction FUNCTION = (type, lexer) -> {
    for (MergeTuple currentTuple : TUPLES) {
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
  };

  public BashLexer() {
    super(new FlexAdapter(
        new _BashLexerGen(null) {
          @Override
          public void reset(CharSequence buffer, int start, int end, int initialState) {
            onReset();
            super.reset(buffer, start, end, initialState);
          }
        })
    );

  }

  @Override
  public MergeFunction getMergeFunction() {
    return FUNCTION;
  }
}