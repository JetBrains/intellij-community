package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.search.UsageSearchContext;
import gnu.trove.TIntIntHashMap;

public class XmlFilterLexer extends BaseFilterLexer {
  private boolean caseInsensitive;

  public XmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts) {
    this(originalLexer, table, todoCounts,false);
  }

  public XmlFilterLexer(Lexer originalLexer, TIntIntHashMap table, int[] todoCounts, boolean _caseInsensitive) {
    super(originalLexer, table, todoCounts);
    caseInsensitive = _caseInsensitive;
  }

  public void advance() {
    IElementType tokenType = myOriginalLexer.getTokenType();
    if (tokenType == ElementType.XML_COMMENT_CHARACTERS) {
      advanceTodoItemCounts(getBuffer(), getTokenStart(), getTokenEnd());
    }

    if (tokenType == ElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
      IdTableBuilding.scanWords(myTable, getBuffer(), getTokenStart(), getTokenEnd(),
                                UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_ALIEN_LANGUAGES, caseInsensitive);
    } else {
      IdTableBuilding.scanWords(myTable, getBuffer(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_PLAIN_TEXT, false);
    }

    myOriginalLexer.advance();
  }
}
