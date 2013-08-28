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
  private final TokenSet myNameDefinerTokens;
  private final TokenSet myKeywordTokens;
  private final TokenSet myParameterTokens;
  private final TokenSet myFunctionDeclarationTokens;
  private final TokenSet myUnbalancedBracesRecoveryTokens;
  private final TokenSet myReferenceExpressionTokens;

  private PythonDialectsTokenSetProvider() {
    TokenSet stmts = TokenSet.EMPTY;
    TokenSet exprs = TokenSet.EMPTY;
    TokenSet definers = TokenSet.EMPTY;
    TokenSet keywords = TokenSet.EMPTY;
    TokenSet parameters = TokenSet.EMPTY;
    TokenSet functionDeclarations = TokenSet.EMPTY;
    TokenSet recoveryTokens = TokenSet.EMPTY;
    TokenSet referenceExpressions = TokenSet.EMPTY;
    for(PythonDialectsTokenSetContributor contributor: Extensions.getExtensions(PythonDialectsTokenSetContributor.EP_NAME)) {
      stmts = TokenSet.orSet(stmts, contributor.getStatementTokens());
      exprs = TokenSet.orSet(exprs, contributor.getExpressionTokens());
      definers = TokenSet.orSet(definers, contributor.getNameDefinerTokens());
      keywords = TokenSet.orSet(keywords, contributor.getKeywordTokens());
      parameters = TokenSet.orSet(parameters, contributor.getParameterTokens());
      functionDeclarations = TokenSet.orSet(functionDeclarations, contributor.getFunctionDeclarationTokens());
      recoveryTokens = TokenSet.orSet(recoveryTokens, contributor.getUnbalancedBracesRecoveryTokens());
      referenceExpressions = TokenSet.orSet(referenceExpressions, contributor.getReferenceExpressionTokens());
    }
    myStatementTokens = stmts;
    myExpressionTokens = exprs;
    myNameDefinerTokens = definers;
    myKeywordTokens = keywords;
    myParameterTokens = parameters;
    myFunctionDeclarationTokens = functionDeclarations;
    myUnbalancedBracesRecoveryTokens = recoveryTokens;
    myReferenceExpressionTokens = referenceExpressions;
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

  public TokenSet getParameterTokens() {
    return myParameterTokens;
  }

  public TokenSet getFunctionDeclarationTokens() {
    return myFunctionDeclarationTokens;
  }

  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return myUnbalancedBracesRecoveryTokens;
  }

  public TokenSet getReferenceExpressionTokens() {
    return myReferenceExpressionTokens;
  }
}
