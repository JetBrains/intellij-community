package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TIntIntHashMap;

/**
 * @author ven
 */
public class PropertiesFilterLexer extends BaseFilterLexer {
  public PropertiesFilterLexer(final Lexer originalLexer, final TIntIntHashMap table, final int[] todoCounts) {
    super(originalLexer, table, todoCounts);
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    if (tokenType == PropertiesTokenTypes.KEY_CHARACTERS) {
      IdTableBuilding.scanWords(myTable, getBufferSequence(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_PLAIN_TEXT);
    }
    else if (PropertiesTokenTypes.COMMENTS.contains(tokenType)) {
      IdTableBuilding.scanWords(myTable, getBufferSequence(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_PLAIN_TEXT);
      advanceTodoItemCounts(getBufferSequence(), getTokenStart(), getTokenEnd());
    }
    else {
      IdTableBuilding.scanWords(myTable, getBufferSequence(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES | UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }
}
