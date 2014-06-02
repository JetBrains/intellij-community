/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;

public class XmlFilterLexer extends BaseFilterLexer {
  static final TokenSet ourNoWordsTokenSet = TokenSet.create(
    XmlTokenType.TAG_WHITE_SPACE,
    TokenType.WHITE_SPACE,
    XmlTokenType.XML_REAL_WHITE_SPACE,
    XmlTokenType.XML_EQ,
    XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER,
    XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,
    XmlTokenType.XML_START_TAG_START,
    XmlTokenType.XML_EMPTY_ELEMENT_END,
    XmlTokenType.XML_END_TAG_START,
    XmlTokenType.XML_TAG_END,
    XmlTokenType.XML_DOCTYPE_END,
    XmlTokenType.XML_COMMENT_START,
    XmlTokenType.XML_COMMENT_END,
    XmlTokenType.XML_PI_START,
    XmlTokenType.XML_PI_END,
    XmlTokenType.XML_CDATA_END
  );

  public XmlFilterLexer(Lexer originalLexer, OccurrenceConsumer table) {
    super(originalLexer, table);
  }

  @Override
  public void advance() {
    final IElementType tokenType = myDelegate.getTokenType();

    if (tokenType == XmlTokenType.XML_COMMENT_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }

    if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, true, false);
    }
    else if (tokenType == XmlTokenType.XML_NAME || tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else if (tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN || tokenType == XmlTokenType.XML_CHAR_ENTITY_REF) {
      scanWordsInToken(UsageSearchContext.IN_CODE, false, false);
    }
    else if (tokenType == XmlElementType.XML_TEXT) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else if (tokenType == XmlTokenType.XML_TAG_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else if (!ourNoWordsTokenSet.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }

    myDelegate.advance();
  }
}
