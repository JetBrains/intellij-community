// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ShLexer extends MergingLexerAdapterBase implements ShTokenTypes {
  private static final MergeFunction FUNCTION = (type, lexer) -> {
    if (type != HEREDOC_CONTENT && type != STRING_CONTENT && type != PARAM_SEPARATOR && type != WORD) return type;

    if (type == WORD) {
      advanceLexerWhile(lexer, WORD);
      return WORD;
    }
    else if (type == HEREDOC_CONTENT) {
      advanceLexerWhile(lexer, HEREDOC_CONTENT);
      return HEREDOC_CONTENT;
    }
    else if (type == PARAM_SEPARATOR) {
      advanceLexerWhile(lexer, PARAM_SEPARATOR);
      return PARAM_SEPARATOR;
    }
    else {
      advanceLexerWhile(lexer, STRING_CONTENT);
      return STRING_CONTENT;
    }
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

  private static void advanceLexerWhile(@NotNull Lexer lexer, @NotNull IElementType condition) {
    IElementType current = lexer.getTokenType();
    while (current == condition) {
      lexer.advance();
      current = lexer.getTokenType();
    }
  }
}