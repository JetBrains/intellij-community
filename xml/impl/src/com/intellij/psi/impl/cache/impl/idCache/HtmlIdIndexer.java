package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;

public class HtmlIdIndexer extends LexerBasedIdIndexer {
  protected Lexer createLexer(final BaseFilterLexer.OccurrenceConsumer consumer) {
    return new XHtmlFilterLexer(new HtmlHighlightingLexer(), consumer);
  }
}
