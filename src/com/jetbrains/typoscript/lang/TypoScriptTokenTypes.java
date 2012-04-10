package com.jetbrains.typoscript.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author lene
 *         Date: 03.04.12
 */
public interface TypoScriptTokenTypes extends TokenType {
  IElementType C_STYLE_COMMENT = new TypoScriptTokenType("c style comment");
  IElementType ONE_LINE_COMMENT = new TypoScriptTokenType("one line comment");

  TokenSet COMMENTS = TokenSet.create(C_STYLE_COMMENT, ONE_LINE_COMMENT);

  IElementType MODIFICATION_OPERATOR = new TypoScriptTokenType(":=");
  IElementType ASSIGNMENT_OPERATOR = new TypoScriptTokenType("=");

  IElementType MULTILINE_VALUE_OPERATOR_BEGIN = new TypoScriptTokenType("(");
  IElementType MULTILINE_VALUE_OPERATOR_END = new TypoScriptTokenType(")");

  IElementType CODE_BLOCK_OPERATOR_BEGIN = new TypoScriptTokenType("{");
  IElementType CODE_BLOCK_OPERATOR_END = new TypoScriptTokenType("}");
  IElementType UNSETTING_OPERATOR = new TypoScriptTokenType(">");
  IElementType COPYING_OPERATOR = new TypoScriptTokenType("<");

  IElementType ASSIGNMENT_VALUE = new TypoScriptTokenType("assignment value");
  IElementType MULTILINE_VALUE = new TypoScriptTokenType("multiline value");
  IElementType IGNORED_TEXT = new TypoScriptTokenType("ignored text");
  
  IElementType MODIFICATION_OPERATOR_FUNCTION = new TypoScriptTokenType("modification function");
  IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN = new TypoScriptTokenType("modification (");
  IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_END = new TypoScriptTokenType("modification )");
  IElementType MODIFICATION_OPERATOR_FUNCTION_ARGUMENT = new TypoScriptTokenType("modification value");
  IElementType OBJECT_PATH_ENTITY = new TypoScriptTokenType("object path entity");
  IElementType OBJECT_PATH_SEPARATOR = new TypoScriptTokenType(".");
  IElementType INCLUDE_STATEMENT = new TypoScriptTokenType("include");
  IElementType CONDITION = new TypoScriptTokenType("condition");
}
