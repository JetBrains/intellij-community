package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;

public class XmlTodoIndexer extends LexerBasedTodoIndexer {
  protected Lexer createLexer(final TodoOccurrenceConsumer consumer) {
    return new XmlFilterLexer(new XmlLexer(), consumer);
  }
}
