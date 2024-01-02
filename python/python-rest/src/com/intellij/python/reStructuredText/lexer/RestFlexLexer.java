// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import com.intellij.python.reStructuredText.RestTokenTypes;

/**
 * User : catherine
 */
public class RestFlexLexer extends MergingLexerAdapter {
  public static final TokenSet TOKENS_TO_MERGE = TokenSet.create(RestTokenTypes.ITALIC, RestTokenTypes.BOLD, RestTokenTypes.FIXED,
                                                           RestTokenTypes.LINE, RestTokenTypes.PYTHON_LINE,
                                                           RestTokenTypes.COMMENT, RestTokenTypes.INLINE_LINE,
                                                           RestTokenTypes.WHITESPACE);
  public RestFlexLexer() {
    super(new FlexAdapter(new _RestFlexLexer(null)), TOKENS_TO_MERGE);
  }
}
