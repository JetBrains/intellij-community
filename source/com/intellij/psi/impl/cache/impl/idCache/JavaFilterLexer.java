package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;

/**
 * @author ven
 */
public class JavaFilterLexer extends BaseFilterLexer {
  public JavaFilterLexer(final Lexer originalLexer, final OccurrenceConsumer table) {
    super(originalLexer, table);
  }

  public void advance() {
    final IElementType tokenType = myOriginalLexer.getTokenType();

    if (tokenType == JavaTokenType.IDENTIFIER
        || tokenType == JavaTokenType.LONG_LITERAL
        || tokenType == JavaTokenType.INTEGER_LITERAL
        || tokenType == JavaTokenType.CHARACTER_LITERAL) {
      addOccurrenceInToken(UsageSearchContext.IN_CODE);
    }
    else if (tokenType == JavaTokenType.STRING_LITERAL) {
      scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true);
    }
    else if (tokenType == JavaTokenType.END_OF_LINE_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT ||
             tokenType == JavaTokenType.DOC_COMMENT) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false);
      advanceTodoItemCountsInToken();
    }
    else if (JavaTokenType.KEYWORD_BIT_SET.contains(tokenType)) {
     // addOccurrenceInToken(UsageSearchContext.IN_PLAIN_TEXT);
    }

    myOriginalLexer.advance();
  }

}
