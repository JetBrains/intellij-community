package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public abstract class PrefixedTokenParser extends BaseTokenParser {
  private char[] myPrefix;
  private final IElementType myTokenType;

  public PrefixedTokenParser(String prefix, IElementType tokenType) {
    myTokenType = tokenType;
    myPrefix = prefix.toCharArray();
  }

  public final boolean hasToken(int position) {
    final int start = position;
    int i;
    for (i = 0; i < myPrefix.length && position < myEndOffset; i++, position++) {
      if (myPrefix[i] != myBuffer.charAt(position)) break;
    }
    if (i < myPrefix.length) return false;
    int end = getTokenEnd(position);
    myTokenInfo.updateData(start, end, myTokenType);
    return true;
  }

  public int getSmartUpdateShift() {
    return myPrefix.length;
  }

  protected abstract int getTokenEnd(int position);
}
