package com.jetbrains.rest.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.rest.RestTokenTypes;

import java.io.Reader;

/**
 * User : catherine
 */
public class RestFlexLexer extends MergingLexerAdapter {
  public static final TokenSet TOKENS_TO_MERGE = TokenSet.create(RestTokenTypes.ITALIC, RestTokenTypes.BOLD, RestTokenTypes.FIXED,
                                                           RestTokenTypes.LINE, RestTokenTypes.PYTHON_LINE,
                                                           RestTokenTypes.COMMENT, RestTokenTypes.INLINE_LINE,
                                                           RestTokenTypes.WHITESPACE);
  public RestFlexLexer() {
    super(new FlexAdapter(new _RestFlexLexer((Reader) null)), TOKENS_TO_MERGE);
  }
}
