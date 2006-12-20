package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.Language;
import gnu.trove.TIntIntHashMap;

public class XHtmlFilterLexer extends BaseFilterLexer {

  public XHtmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    super(originalLexer, table, todoCounts);
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    final CharSequence buffer = getBufferSequence();
    final int tokenStart = getTokenStart();
    final int tokenEnd = getTokenEnd();

    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd, UsageSearchContext.IN_COMMENTS);
      advanceTodoItemCounts(buffer, tokenStart, tokenEnd);
    } else if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN || 
        tokenType == ElementType.XML_NAME ||
        tokenType == ElementType.XML_TAG_NAME
       ) {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd,
                                UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      IdTableBuilding.processPossibleComplexFileName(buffer, tokenStart, tokenEnd, myTable);
    } else if (tokenType.getLanguage() != StdLanguages.XML &&
      tokenType.getLanguage() != Language.ANY         
    ) {
      boolean inComments = IdCacheUtil.isInComments(tokenType);

      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd,
                                (inComments)?UsageSearchContext.IN_COMMENTS:UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      IdTableBuilding.processPossibleComplexFileName(buffer, tokenStart, tokenEnd, myTable);
      
      if (inComments) advanceTodoItemCounts(buffer, tokenStart, tokenEnd);
    } else {
      IdTableBuilding.scanWords(myTable, buffer, tokenStart, tokenEnd, UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }

}
