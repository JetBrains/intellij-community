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
    final IElementType tokenType = myOriginalLexer.getTokenType();

    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS);
      advanceTodoItemCountsInToken();
    } else if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN || 
        tokenType == ElementType.XML_NAME ||
        tokenType == ElementType.XML_TAG_NAME
       ) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN) IdTableBuilding.processPossibleComplexFileName(myBuffer, getTokenStart(),
                                                                                                             getTokenEnd(), myTable);
    } else if (tokenType.getLanguage() != StdLanguages.XML &&
      tokenType.getLanguage() != Language.ANY         
    ) {
      boolean inComments = IdCacheUtil.isInComments(tokenType);

      scanWordsInToken((inComments)?UsageSearchContext.IN_COMMENTS:UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      IdTableBuilding.processPossibleComplexFileName(myBuffer, getTokenStart(), getTokenEnd(), myTable);
      
      if (inComments) advanceTodoItemCountsInToken();
    } else {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }

}
