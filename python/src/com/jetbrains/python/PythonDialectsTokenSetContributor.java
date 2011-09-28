package com.jetbrains.python;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.TokenSet;

/**
 * @author vlan
 */
public interface PythonDialectsTokenSetContributor {
  ExtensionPointName<PythonDialectsTokenSetContributor> EP_NAME = ExtensionPointName.create("Pythonid.dialectsTokenSetContributor");

  TokenSet getStatementTokens();
  TokenSet getExpressionTokens();
  TokenSet getNameDefinerTokens();
  TokenSet getKeywordTokens();
}
