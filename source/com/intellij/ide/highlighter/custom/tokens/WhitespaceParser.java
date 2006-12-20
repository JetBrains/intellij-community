package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;


/**
 * @author dsl
 */
public class WhitespaceParser extends BaseTokenParser {

  public boolean hasToken(int position) {
    if (!Character.isWhitespace(myBuffer.charAt(position))) return false;
    int start = position;
    for (position++; position < myEndOffset && Character.isWhitespace(myBuffer.charAt(position)); position++) ;
    myTokenInfo.updateData(start, position, CustomHighlighterTokenType.WHITESPACE);
    return true;
  }

  public int getSmartUpdateShift() {
    return 0;
  }
}
