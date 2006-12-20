package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TIntIntHashMap;

/**
 * @author ven
 */
public class JavaFilterLexer extends BaseFilterLexer {
  public JavaFilterLexer(final Lexer originalLexer, final TIntIntHashMap table, final int[] todoCounts) {
    super(originalLexer, table, todoCounts);
  }

  public void advance() {
    final IElementType tokenType = myOriginalLexer.getTokenType();

    if (tokenType == JavaTokenType.IDENTIFIER
        || tokenType == JavaTokenType.LONG_LITERAL 
        || tokenType == JavaTokenType.INTEGER_LITERAL
        || tokenType == JavaTokenType.CHARACTER_LITERAL) {
      int start = getTokenStart();
      int end = getTokenEnd();
      IdCacheUtil.addOccurrence(myTable, myBuffer, start, end, UsageSearchContext.IN_CODE);
    }
    else if (tokenType == JavaTokenType.STRING_LITERAL) {
      scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES);
    }
    else if (tokenType == JavaTokenType.END_OF_LINE_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT ||
             tokenType == JavaTokenType.DOC_COMMENT) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS);
      advanceTodoItemCountsInToken();
    }
    else if (JavaTokenType.KEYWORD_BIT_SET.contains(tokenType)) {
      int start = getTokenStart();
      int end = getTokenEnd();
      IdCacheUtil.addOccurrence(myTable, myBuffer, start, end, UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }

}
