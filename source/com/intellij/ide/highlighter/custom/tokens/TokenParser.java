package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.ide.highlighter.custom.tokens.TokenInfo;

/**
 * @author dsl
 */
public interface TokenParser {
  void setBuffer(char[] buffer, int startOffset, int endOffset);
  boolean hasToken(int position);
  void getTokenInfo(TokenInfo info);
  int getSmartUpdateShift();
}
