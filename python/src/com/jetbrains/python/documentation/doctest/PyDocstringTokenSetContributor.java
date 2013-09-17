package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyDocstringTokenSetContributor extends PythonDialectsTokenSetContributorBase {
  public static final TokenSet DOCSTRING_REFERENCE_EXPRESSIONS = TokenSet.create(PyDocstringTokenTypes.DOC_REFERENCE);

  @NotNull
  @Override
  public TokenSet getExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }

  @NotNull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }
}
