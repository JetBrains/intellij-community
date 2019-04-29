package com.intellij.bash.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.tree.IElementType;

public class BashLexer extends MergingLexerAdapterBase implements BashTokenTypes {
  private static final MergeFunction FUNCTION = (type, lexer) -> {
    if (type != HEREDOC_CONTENT) return type;

    IElementType current = lexer.getTokenType();
    while (current == HEREDOC_CONTENT) {
      lexer.advance();
      current = lexer.getTokenType();
    }
    return HEREDOC_CONTENT;
  };

  public BashLexer() {
    super(new FlexAdapter(new _BashLexerGen(null) {
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