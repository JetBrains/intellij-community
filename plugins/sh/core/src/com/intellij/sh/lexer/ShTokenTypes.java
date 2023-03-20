// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShTokenType;


public interface ShTokenTypes extends ShTypes {
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  // common types
  IElementType WHITESPACE = TokenType.WHITE_SPACE;
  IElementType LINE_CONTINUATION = new ShTokenType("line continuation \\");

  TokenSet whitespaceTokens = TokenSet.create(WHITESPACE, LINE_CONTINUATION);

  // comments
  IElementType COMMENT = new ShTokenType("Comment");

  TokenSet commentTokens = TokenSet.create(COMMENT, SHEBANG);
  TokenSet literals = TokenSet.create(STRING_CONTENT, RAW_STRING, INT, HEX, OCTAL);

  TokenSet HUMAN_READABLE_KEYWORDS_WITHOUT_TEMPLATES = TokenSet.create(
      DO, DONE, ELSE, ESAC, FI, IN, THEN
  );

  //conditional expressions
  TokenSet HUMAN_READABLE_KEYWORDS = TokenSet.create(
      CASE, DO, DONE,
      ELIF, ELSE, ESAC, FI, FOR, FUNCTION,
      IF, IN, SELECT, THEN, UNTIL, WHILE
  );

  TokenSet keywords = TokenSet.orSet(HUMAN_READABLE_KEYWORDS, TokenSet.create(
      BANG,
      LEFT_DOUBLE_BRACKET, RIGHT_DOUBLE_BRACKET,
      CASE_END,
      DOLLAR,
      EXPR_CONDITIONAL_LEFT, EXPR_CONDITIONAL_RIGHT
  ));

  //these are keyword tokens which may be used as identifiers, e.g. in a for loop
  //these tokens will be remapped to word tokens if they occur at a position where a word token would be accepted
  TokenSet identifierKeywords = TokenSet.create(
      CASE, DO, DONE, ELIF, ELSE, ESAC, FI, FOR, FUNCTION,
      IF, IN, SELECT, THEN, UNTIL, WHILE, TEST, LET, EVAL
  );

  TokenSet stringLiterals = TokenSet.create(WORD, RAW_STRING);

  TokenSet arithmeticOperationsForRemapping = TokenSet.create(PLUS, MINUS, DIV, MULT, MOD,MINUS_MINUS, PLUS_PLUS, COLON, QMARK);
  TokenSet numbers = TokenSet.create(INT, OCTAL, HEX);
}
