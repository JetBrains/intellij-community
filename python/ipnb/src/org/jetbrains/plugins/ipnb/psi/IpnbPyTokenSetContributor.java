package org.jetbrains.plugins.ipnb.psi;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import org.jetbrains.annotations.NotNull;

public class IpnbPyTokenSetContributor extends PythonDialectsTokenSetContributorBase {
  public static final TokenSet IPNB_REFERENCE_EXPRESSIONS = TokenSet.create(IpnbPyTokenTypes.IPNB_REFERENCE, IpnbPyTokenTypes.IPNB_TARGET);

  @NotNull
  @Override
  public TokenSet getExpressionTokens() {
    return IPNB_REFERENCE_EXPRESSIONS;
  }

  @NotNull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return IPNB_REFERENCE_EXPRESSIONS;
  }


  @NotNull
  @Override
  public TokenSet getFunctionDeclarationTokens() {
    return TokenSet.create(IpnbPyTokenTypes.IPNB_FUNCTION);
  }
}
