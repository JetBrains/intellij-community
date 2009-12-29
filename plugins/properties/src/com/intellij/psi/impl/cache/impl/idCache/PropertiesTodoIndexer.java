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

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Maxim.Mossienko
 */
public class PropertiesTodoIndexer extends LexerBasedTodoIndexer {
  static TokenSet WHITE_SPACE_SET = TokenSet.create(TokenType.WHITE_SPACE);

  protected Lexer createLexer(final TodoOccurrenceConsumer consumer) {
    final PropertiesFilterLexer propsLexer = new PropertiesFilterLexer(new PropertiesLexer(), consumer);
    return new FilterLexer(propsLexer, new FilterLexer.SetFilter(WHITE_SPACE_SET));
  }
}
