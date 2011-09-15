package com.jetbrains.python;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.tree.TokenSet;

/**
 * @author vlan
 */
public class PythonDialectsTokenSetProvider {
  public static PythonDialectsTokenSetProvider INSTANCE = new PythonDialectsTokenSetProvider();
  public ExtensionPointName<PythonDialectsTokenSetContributor> EP_NAME = ExtensionPointName.create("Pythonid.dialectsTokenSetContributor");

  private final TokenSet myStatementTokens;
  private final TokenSet myExpressionTokens;
  private TokenSet myNameDefinerTokens;

  private PythonDialectsTokenSetProvider() {
    TokenSet stmts = TokenSet.EMPTY;
    TokenSet exprs = TokenSet.EMPTY;
    TokenSet definers = TokenSet.EMPTY;
    for(PythonDialectsTokenSetContributor contributor: Extensions.getExtensions(EP_NAME)) {
      stmts = TokenSet.orSet(stmts, contributor.getStatementTokens());
      exprs = TokenSet.orSet(exprs, contributor.getExpressionTokens());
      definers = TokenSet.orSet(definers, contributor.getNameDefinerTokens());
    }
    myStatementTokens = stmts;
    myExpressionTokens = exprs;
    myNameDefinerTokens = definers;
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
}
