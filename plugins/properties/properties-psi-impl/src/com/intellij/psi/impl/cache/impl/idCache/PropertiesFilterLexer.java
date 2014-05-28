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

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;

/**
 * @author ven
 */
public class PropertiesFilterLexer extends BaseFilterLexer {
  public PropertiesFilterLexer(final Lexer originalLexer, final OccurrenceConsumer table) {
    super(originalLexer, table);
  }

  public void advance() {
    final IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == PropertiesTokenTypes.KEY_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }
    else if (PropertiesTokenTypes.COMMENTS.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_PLAIN_TEXT, false, false);
      advanceTodoItemCountsInToken();
    }
    else {
      scanWordsInToken(UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }

    getDelegate().advance();
  }
}
