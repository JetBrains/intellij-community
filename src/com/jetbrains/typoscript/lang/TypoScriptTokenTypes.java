package com.jetbrains.typoscript.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author lene
 *         Date: 03.04.12
 */
public interface TypoScriptTokenTypes extends TokenType {
  IElementType C_STYLE_COMMENT = new TypoScriptTokenType("C_STYLE_COMMENT");
  IElementType ONE_LINE_COMMENT = new TypoScriptTokenType("ONE_LINE_COMMENT");
  IElementType IGNORED_TEXT = new TypoScriptTokenType("IGNORED_TEXT");
  IElementType WHITE_SPACE_WITH_NEW_LINE = new TypoScriptTokenType("WHITE_SPACE_WITH_NEW_LINE");

  IElementType ASSIGNMENT_OPERATOR = new TypoScriptTokenType("ASSIGN");
  IElementType ASSIGNMENT_VALUE = new TypoScriptTokenType("ASSIGNMENT_VALUE");

  IElementType MULTILINE_VALUE_OPERATOR_BEGIN = new TypoScriptTokenType("MULTILINE_OPEN");
  IElementType MULTILINE_VALUE_OPERATOR_END = new TypoScriptTokenType("MULTILINE_CLOSE");
  IElementType MULTILINE_VALUE = new TypoScriptTokenType("MULTILINE_VALUE");

  IElementType CODE_BLOCK_OPERATOR_BEGIN = new TypoScriptTokenType("CODE_BLOCK_OPEN");
  IElementType CODE_BLOCK_OPERATOR_END = new TypoScriptTokenType("CODE_BLOCK_CLOSE");

  IElementType UNSETTING_OPERATOR = new TypoScriptTokenType("UNSET");
  IElementType COPYING_OPERATOR = new TypoScriptTokenType("COPY");

  IElementType MODIFICATION_OPERATOR = new TypoScriptTokenType("MODIFY");
  IElementType MODIFICATION_OPERATOR_FUNCTION = new TypoScriptTokenType("MODIFICATION_FUNCTION");
  IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN = new TypoScriptTokenType("MODIFICATION_OPEN");
  IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_END = new TypoScriptTokenType("MODIFICATION_CLOSE");
  IElementType MODIFICATION_OPERATOR_FUNCTION_ARGUMENT = new TypoScriptTokenType("MODIFICATION_VALUE");

  IElementType OBJECT_PATH_ENTITY = new TypoScriptTokenType("OBJECT_PATH_ENTITY");
  IElementType OBJECT_PATH_SEPARATOR = new TypoScriptTokenType("OBJECT_PATH_SEPARATOR");

  IElementType INCLUDE_STATEMENT = new TypoScriptTokenType("INCLUDE");
  IElementType CONDITION = new TypoScriptTokenType("CONDITION");

  TokenSet OPERATORS = TokenSet.create(MODIFICATION_OPERATOR, ASSIGNMENT_OPERATOR,
                                       MULTILINE_VALUE_OPERATOR_BEGIN, MULTILINE_VALUE_OPERATOR_END,
                                       CODE_BLOCK_OPERATOR_BEGIN, CODE_BLOCK_OPERATOR_END,
                                       UNSETTING_OPERATOR, COPYING_OPERATOR);

  TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE , WHITE_SPACE_WITH_NEW_LINE);
  TokenSet COMMENTS = TokenSet.create(ONE_LINE_COMMENT, C_STYLE_COMMENT, IGNORED_TEXT);
  TokenSet STRING_LITERALS = TokenSet.create(ASSIGNMENT_VALUE, MULTILINE_VALUE);
}
