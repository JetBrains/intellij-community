/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.jira.jql;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Mikhail Golubev
 */
public interface JqlTokenTypes {
  IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  IElementType AND_KEYWORD = new JqlElementType("AND_KEYWORD");
  IElementType OR_KEYWORD = new JqlElementType("OR_KEYWORD");
  IElementType NOT_KEYWORD = new JqlElementType("NOT_KEYWORD");
  IElementType EMPTY_KEYWORD = new JqlElementType("EMPTY_KEYWORD");
  IElementType NULL_KEYWORD = new JqlElementType("NULL_KEYWORD");
  IElementType ORDER_KEYWORD = new JqlElementType("ORDER_KEYWORD");
  IElementType BY_KEYWORD = new JqlElementType("BY_KEYWORD");
  IElementType WAS_KEYWORD = new JqlElementType("WAS_KEYWORD");
  IElementType IS_KEYWORD = new JqlElementType("IS_KEYWORD");
  IElementType IN_KEYWORD = new JqlElementType("IN_KEYWORD");
  IElementType ASC_KEYWORD = new JqlElementType("ASC_KEYWORD");
  IElementType DESC_KEYWORD = new JqlElementType("DESC_KEYWORD");

  IElementType CHANGED_KEYWORD = new JqlElementType("CHANGED_KEYWORD");
  IElementType FROM_KEYWORD = new JqlElementType("FROM_KEYWORD");
  IElementType TO_KEYWORD = new JqlElementType("TO_KEYWORD");
  IElementType ON_KEYWORD = new JqlElementType("ON_KEYWORD");
  IElementType DURING_KEYWORD = new JqlElementType("DURING_KEYWORD");
  IElementType BEFORE_KEYWORD = new JqlElementType("BEFORE_KEYWORD");
  IElementType AFTER_KEYWORD = new JqlElementType("AFTER_KEYWORD");

  IElementType STRING_LITERAL = new JqlElementType("STRING_LITERAL");
  IElementType NUMBER_LITERAL = new JqlElementType("NUMBER_LITERAL");
  IElementType CUSTOM_FIELD = new JqlElementType("CUSTOM_FIELD");
  // operators
  IElementType EQ = new JqlElementType("EQ"); // =
  IElementType NE = new JqlElementType("NE"); // !=
  IElementType LT = new JqlElementType("LT"); // <
  IElementType GT = new JqlElementType("GT"); // >
  IElementType LE = new JqlElementType("LE"); // <=
  IElementType GE = new JqlElementType("GE"); // >=
  IElementType LPAR = new JqlElementType("LPAR"); // (
  IElementType RPAR = new JqlElementType("RPAR"); // )
  IElementType CONTAINS = new JqlElementType("CONTAINS"); // ~
  IElementType NOT_CONTAINS = new JqlElementType("NOT_CONTAINS"); // !~
  IElementType COMMA = new JqlElementType("COMMA"); // ,
  IElementType AMP = new JqlElementType("AMP"); // &
  IElementType AMP_AMP = new JqlElementType("AMP_AMP"); // &&
  IElementType PIPE = new JqlElementType("PIPE"); // |
  IElementType PIPE_PIPE = new JqlElementType("PIPE_PIPE"); // ||
  IElementType BANG = new JqlElementType("BANG"); // !
  /**
   * Well-known tokes types
   */
  TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
  TokenSet KEYWORDS = TokenSet.create(
    AND_KEYWORD,
    OR_KEYWORD,
    NOT_KEYWORD,
    IS_KEYWORD,
    EMPTY_KEYWORD,
    NULL_KEYWORD,
    IN_KEYWORD,
    WAS_KEYWORD,
    CHANGED_KEYWORD,
    FROM_KEYWORD,
    TO_KEYWORD,
    BY_KEYWORD,
    DURING_KEYWORD,
    AFTER_KEYWORD,
    BEFORE_KEYWORD,
    ORDER_KEYWORD,
    ON_KEYWORD,
    ASC_KEYWORD,
    DESC_KEYWORD
  );
  TokenSet SIMPLE_OPERATORS = TokenSet.create(EQ, NE, LT, LE, GT, GE, CONTAINS, NOT_CONTAINS);

  TokenSet SIGN_OPERATORS = TokenSet.create(
    EQ, NE, LT, LE, GT, GE, CONTAINS, NOT_CONTAINS, AMP, AMP_AMP, PIPE, PIPE_PIPE, BANG
  );
  TokenSet AND_OPERATORS = TokenSet.create(AND_KEYWORD, AMP_AMP, AMP);
  TokenSet OR_OPERATORS = TokenSet.create(OR_KEYWORD, PIPE_PIPE, PIPE);

  TokenSet NOT_OPERATORS = TokenSet.create(NOT_KEYWORD, BANG);
  TokenSet HISTORY_PREDICATES = TokenSet.create(
    ON_KEYWORD, BEFORE_KEYWORD, AFTER_KEYWORD, DURING_KEYWORD, FROM_KEYWORD, TO_KEYWORD, BY_KEYWORD
  );
  TokenSet SORT_ORDERS = TokenSet.create(ASC_KEYWORD, DESC_KEYWORD);
  TokenSet EMPTY_VALUES = TokenSet.create(EMPTY_KEYWORD, NULL_KEYWORD);
  TokenSet LITERALS = TokenSet.create(NUMBER_LITERAL, STRING_LITERAL);

  /**
   * Any properly escaped literal can be used as field name according to weird JQL grammar
   * @see JqlElementTypes
   */
  TokenSet VALID_FIELD_NAMES = TokenSet.create(STRING_LITERAL, NUMBER_LITERAL, CUSTOM_FIELD);
  TokenSet VALID_ARGUMENTS = LITERALS;
}
