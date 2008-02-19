package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;

public class XmlFilterLexer extends BaseFilterLexer {

  public XmlFilterLexer(Lexer originalLexer, OccurrenceConsumer table) {
    super(originalLexer, table);
  }

  public void advance() {
    final IElementType tokenType = myOriginalLexer.getTokenType();

    if (tokenType == XmlElementType.XML_COMMENT_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }

    if (tokenType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, true, false);
    }
    else if (tokenType == XmlElementType.XML_NAME || tokenType == XmlElementType.XML_DATA_CHARACTERS) {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, false);
    }
    else if (tokenType == XmlElementType.XML_ENTITY_REF_TOKEN || tokenType == XmlElementType.XML_CHAR_ENTITY_REF) {
      scanWordsInToken(UsageSearchContext.IN_CODE, false, false);
    }
    else {
      scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false);
    }

    myOriginalLexer.advance();
  }
}
