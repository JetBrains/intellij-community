// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface PropertiesTokenTypes {
  IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
  IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

  IElementType END_OF_LINE_COMMENT = new PropertiesElementType("END_OF_LINE_COMMENT");
  IElementType KEY_CHARACTERS = new PropertiesElementType("KEY_CHARACTERS");
  IElementType VALUE_CHARACTERS = new PropertiesElementType("VALUE_CHARACTERS");
  IElementType KEY_VALUE_SEPARATOR = new PropertiesElementType("KEY_VALUE_SEPARATOR");

  TokenSet COMMENTS = TokenSet.create(END_OF_LINE_COMMENT);
  TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
}
