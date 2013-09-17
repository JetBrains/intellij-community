package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonTokenSetContributor;

/**
 * User : ktisha
 */
public class PyDocstringTokenSetContributor extends PythonTokenSetContributor {
  public static final TokenSet DOCSTRING_REFERENCE_EXPRESSIONS = TokenSet.create(PyDocstringTokenTypes.DOC_REFERENCE);

  @Override
  public TokenSet getExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }

  @Override
  public TokenSet getReferenceExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }
}
