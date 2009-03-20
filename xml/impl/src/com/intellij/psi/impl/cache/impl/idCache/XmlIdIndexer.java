package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;

public class XmlIdIndexer extends LexerBasedIdIndexer {
  protected Lexer createLexer(final BaseFilterLexer.OccurrenceConsumer consumer) {
    return new XmlFilterLexer(new XmlLexer(), consumer);
  }
}
