package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlHighlightingLexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;

public class XHtmlIdIndexer extends LexerBasedIdIndexer {
  protected Lexer createLexer(final BaseFilterLexer.OccurrenceConsumer consumer) {
    return new XHtmlFilterLexer(new XHtmlHighlightingLexer(), consumer);
  }
}
