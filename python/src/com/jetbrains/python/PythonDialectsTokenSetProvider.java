package com.jetbrains.python;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.tree.TokenSet;

/**
 * @author vlan
 */
public class PythonDialectsTokenSetProvider {
  public static final PythonDialectsTokenSetProvider INSTANCE = new PythonDialectsTokenSetProvider();

  private final TokenSet myStatementTokens;
  private final TokenSet myExpressionTokens;
  private TokenSet myNameDefinerTokens;
  private TokenSet myKeywordTokens;

  private PythonDialectsTokenSetProvider() {
    TokenSet stmts = TokenSet.EMPTY;
    TokenSet exprs = TokenSet.EMPTY;
    TokenSet definers = TokenSet.EMPTY;
    TokenSet keywords = TokenSet.EMPTY;
    for(PythonDialectsTokenSetContributor contributor: Extensions.getExtensions(PythonDialectsTokenSetContributor.EP_NAME)) {
      stmts = TokenSet.orSet(stmts, contributor.getStatementTokens());
      exprs = TokenSet.orSet(exprs, contributor.getExpressionTokens());
      definers = TokenSet.orSet(definers, contributor.getNameDefinerTokens());
      keywords = TokenSet.orSet(keywords, contributor.getKeywordTokens());
    }
    myStatementTokens = stmts;
    myExpressionTokens = exprs;
    myNameDefinerTokens = definers;
    myKeywordTokens = keywords;
  }

  public TokenSet getStatementTokens() {
    return myStatementTokens;
  }

  public TokenSet getExpressionTokens() {
    return myExpressionTokens;
  }

  public TokenSet getNameDefinerTokens() {
    return myNameDefinerTokens;
  }

  public TokenSet getKeywordTokens() {
    return myKeywordTokens;
  }
}
