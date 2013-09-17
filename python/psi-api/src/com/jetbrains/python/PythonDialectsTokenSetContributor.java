package com.jetbrains.python;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes element types of various kinds specific for a particular Python dialect.
 *
 * @author vlan
 */
public interface PythonDialectsTokenSetContributor {
  ExtensionPointName<PythonDialectsTokenSetContributor> EP_NAME = ExtensionPointName.create("Pythonid.dialectsTokenSetContributor");

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
   */
  @NotNull
  TokenSet getStatementTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
   */
  @NotNull
  TokenSet getExpressionTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.NameDefiner}.
   */
  @NotNull
  TokenSet getNameDefinerTokens();

  /**
   * Returns element types that are language keywords.
   */
  @NotNull
  TokenSet getKeywordTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
   */
  @NotNull
  TokenSet getParameterTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
   */
  @NotNull
  TokenSet getFunctionDeclarationTokens();

  /**
   * Returns element types that can be used as unbalanced braces recovery tokens in the lexer.
   */
  @NotNull
  TokenSet getUnbalancedBracesRecoveryTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
   */
  @NotNull
  TokenSet getReferenceExpressionTokens();
}
