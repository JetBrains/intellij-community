package com.jetbrains.python;

import com.intellij.psi.tree.TokenSet;

/**
 * @author vlan
 */
public interface PythonDialectsTokenSetContributor {
  TokenSet getStatementTokens();
  TokenSet getExpressionTokens();
  TokenSet getNameDefinerTokens();
}
