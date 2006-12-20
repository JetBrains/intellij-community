package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;


/**
 * @author dsl
 */
public class LineCommentParser extends PrefixedTokenParser {
  private LineCommentParser(String prefix) {
    super(prefix, CustomHighlighterTokenType.LINE_COMMENT);
  }

  protected int getTokenEnd(int position) {
    for (; position < myEndOffset; position++) {
      if (myBuffer.charAt(position) == '\n') break;
    }
    return position;
  }

  public static LineCommentParser create(String prefix) {
    final String trimmedPrefix = prefix.trim();
    if (!"".equals(trimmedPrefix)) {
      return new LineCommentParser(prefix);
    } else {
      return null;
    }
  }
}
