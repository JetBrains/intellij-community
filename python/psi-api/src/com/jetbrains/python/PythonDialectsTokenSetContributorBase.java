package com.jetbrains.python;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public abstract class PythonDialectsTokenSetContributorBase implements PythonDialectsTokenSetContributor {
  @NotNull
  @Override
  public TokenSet getStatementTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getExpressionTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getNameDefinerTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getKeywordTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getParameterTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getFunctionDeclarationTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return TokenSet.EMPTY;
  }
}
