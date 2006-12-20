package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TIntIntHashMap;

public class XmlFilterLexer extends BaseFilterLexer {

  public XmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    super(originalLexer, table, todoCounts);
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    final CharSequence buffer = getBufferSequence();
    final int tokenStart = getTokenStart();
    final int tokenEnd = getTokenEnd();

    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS);
      advanceTodoItemCountsInToken();
    }

    if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
      IdTableBuilding.processPossibleComplexFileName(buffer, tokenStart, tokenEnd, myTable);
    } else if (tokenType == ElementType.XML_NAME || tokenType == ElementType.XML_DATA_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES);
    } else {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }
}
