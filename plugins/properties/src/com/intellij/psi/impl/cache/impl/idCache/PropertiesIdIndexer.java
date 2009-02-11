package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 11.02.2009
* Time: 20:34:27
* To change this template use File | Settings | File Templates.
*/
public class PropertiesIdIndexer extends LexerBasedIdIndexer {
  protected Lexer createLexer(final BaseFilterLexer.OccurrenceConsumer consumer) {
    final PropertiesFilterLexer propsLexer =
      new PropertiesFilterLexer(new PropertiesLexer(), consumer);
    return new FilterLexer(propsLexer, new FilterLexer.SetFilter(PropertiesTodoIndexer.WHITE_SPACE_SET));
  }
}
