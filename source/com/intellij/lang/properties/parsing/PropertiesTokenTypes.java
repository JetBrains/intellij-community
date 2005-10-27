package com.intellij.lang.properties.parsing;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:40:01 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesTokenTypes {
  IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  IElementType END_OF_LINE_COMMENT = new PropertiesElementType("END_OF_LINE_COMMENT");
  IElementType KEY_CHARACTERS = new PropertiesElementType("KEY_CHARACTERS");
  IElementType VALUE_CHARACTERS = new PropertiesElementType("VALUE_CHARACTERS");
  IElementType KEY_VALUE_SEPARATOR = new PropertiesElementType("KEY_VALUE_SEPARATOR");

  TokenSet COMMENTS = TokenSet.create(END_OF_LINE_COMMENT);
  TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
  TokenSet PROPERTIES_TYPES_TO_IGNORE = TokenSet.create(END_OF_LINE_COMMENT, VALUE_CHARACTERS);
}
