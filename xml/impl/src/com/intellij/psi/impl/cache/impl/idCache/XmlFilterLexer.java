/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;

public class XmlFilterLexer extends BaseFilterLexer {

  public XmlFilterLexer(Lexer originalLexer, OccurrenceConsumer table) {
    super(originalLexer, table);
  }

  public void advance() {
    final IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == XmlElementType.XML_COMMENT_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }

    if (tokenType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, true, false);
    }
    else if (tokenType == XmlElementType.XML_NAME || tokenType == XmlElementType.XML_DATA_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else if (tokenType == XmlElementType.XML_ENTITY_REF_TOKEN || tokenType == XmlElementType.XML_CHAR_ENTITY_REF) {
      scanWordsInToken(UsageSearchContext.IN_CODE, false, false);
    }
    else if (tokenType == XmlElementType.XML_TEXT) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }

    getDelegate().advance();
  }
}
