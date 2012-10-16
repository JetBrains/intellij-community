package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonTokenSetContributor;

/**
 * User : ktisha
 */
public class PyDocstringTokenSetContributor extends PythonTokenSetContributor {
  @Override
  public TokenSet getExpressionTokens() {
    return TokenSet.orSet(super.getExpressionTokens(), TokenSet.create(PyDocstringTokenTypes.DOC_REFERENCE));
  }
}
