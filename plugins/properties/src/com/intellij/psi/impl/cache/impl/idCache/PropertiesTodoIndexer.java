package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 11.02.2009
* Time: 20:34:15
* To change this template use File | Settings | File Templates.
*/
public class PropertiesTodoIndexer extends LexerBasedTodoIndexer {
  static TokenSet WHITE_SPACE_SET = TokenSet.create(TokenType.WHITE_SPACE);
  
  protected Lexer createLexer(final TodoOccurrenceConsumer consumer) {
    final PropertiesFilterLexer propsLexer =
      new PropertiesFilterLexer(new PropertiesLexer(), consumer);
    return new FilterLexer(propsLexer, new FilterLexer.SetFilter(WHITE_SPACE_SET));
  }
}
