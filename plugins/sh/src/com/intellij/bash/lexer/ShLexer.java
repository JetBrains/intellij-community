package com.intellij.bash.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.tree.IElementType;

public class ShLexer extends MergingLexerAdapterBase implements ShTokenTypes {
  private static final MergeFunction FUNCTION = (type, lexer) -> {
    if (type != HEREDOC_CONTENT) return type;

    IElementType current = lexer.getTokenType();
    while (current == HEREDOC_CONTENT) {
      lexer.advance();
      current = lexer.getTokenType();
    }
    return HEREDOC_CONTENT;
  };

  public ShLexer() {
    super(new FlexAdapter(new _ShLexerGen(null) {
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