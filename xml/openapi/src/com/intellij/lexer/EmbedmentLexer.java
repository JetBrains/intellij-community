package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * @author Maxim.Mossienko
 */
public interface EmbedmentLexer {
  int getEmbeddedInitialState(final IElementType tokenType);
}
