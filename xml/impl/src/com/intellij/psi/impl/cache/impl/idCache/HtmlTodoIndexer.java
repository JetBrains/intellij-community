package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;

public class HtmlTodoIndexer extends LexerBasedTodoIndexer {
  protected Lexer createLexer(final TodoOccurrenceConsumer consumer) {
    return new XHtmlFilterLexer(new HtmlHighlightingLexer(), consumer);
  }
}
