package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: May 13, 2005
 * Time: 1:34:29 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ELHostLexer {
  void setElTypes(IElementType elTokenTypeForContent,IElementType elTokenTypeForAttribute);
}
