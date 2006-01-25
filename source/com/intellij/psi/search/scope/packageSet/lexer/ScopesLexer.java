package com.intellij.psi.search.scope.packageSet.lexer;

import com.intellij.lexer.FlexAdapter;


/**
 * User: anna
 * Date: 25-Jan-2006
 */
public class ScopesLexer extends FlexAdapter {
  public ScopesLexer() {
    super(new _ScopesLexer());
  }
}
