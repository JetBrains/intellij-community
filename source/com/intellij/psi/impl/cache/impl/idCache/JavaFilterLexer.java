package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
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
    IElementType tokenType = myOriginalLexer.getTokenType();
    if (tokenType == JavaTokenType.IDENTIFIER) {
      int start = getTokenStart();
      int end = getTokenEnd();
      int hashCode = StringUtil.stringHashCode(getBuffer(), start, end - start);
      IdCacheUtil.addOccurrence(myTable, hashCode, UsageSearchContext.IN_CODE);
    }
    else if (tokenType == JavaTokenType.STRING_LITERAL) {
      IdTableBuilding.scanWords(myTable, getBuffer(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_STRINGS, false);
    }
    else if (tokenType == JavaTokenType.END_OF_LINE_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT ||
             tokenType == JavaTokenType.DOC_COMMENT) {
      IdTableBuilding.scanWords(myTable, getBuffer(), getTokenStart(), getTokenEnd(), UsageSearchContext.IN_COMMENTS, false);
      advanceTodoItemCounts(getBuffer(), getTokenStart(), getTokenEnd());
    }

    myOriginalLexer.advance();
  }

}
