package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IChameleonElementType;
import gnu.trove.TIntIntHashMap;

public class XHtmlFilterLexer extends BaseFilterLexer {

  public XHtmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    super(originalLexer, table, todoCounts);
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    final char[] buffer = getBuffer();
    final int tokenStart = getTokenStart();
    final int tokenEnd = getTokenEnd();
    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd, UsageSearchContext.IN_COMMENTS);
      advanceTodoItemCounts(buffer, tokenStart, tokenEnd);
    }

    if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN || 
        tokenType == ElementType.XML_NAME ||
        tokenType instanceof IChameleonElementType
       ) {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd,
                                UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      IdCacheUtil.processPossibleComplexFileName(buffer, tokenStart, tokenEnd, myTable);
    } else {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd, UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }
}
