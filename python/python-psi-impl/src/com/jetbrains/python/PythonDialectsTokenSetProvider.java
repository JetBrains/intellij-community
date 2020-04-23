// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.psi.tree.TokenSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Provides element types of various kinds for known Python dialects.
 *
 * @author vlan
 */
@Service
public final class PythonDialectsTokenSetProvider {

  @NotNull
  public static PythonDialectsTokenSetProvider getInstance() {
    return ApplicationManager.getApplication().getService(PythonDialectsTokenSetProvider.class);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
   */
  public TokenSet getStatementTokens() {
    return orSets(PythonDialectsTokenSetContributor::getStatementTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
   */
  public TokenSet getExpressionTokens() {
    return orSets(PythonDialectsTokenSetContributor::getExpressionTokens);
  }

  /**
   * Returns all element types of Python dialects that are language keywords.
   */
  public TokenSet getKeywordTokens() {
    return orSets(PythonDialectsTokenSetContributor::getKeywordTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
   */
  public TokenSet getParameterTokens() {
    return orSets(PythonDialectsTokenSetContributor::getParameterTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
   */
  public TokenSet getFunctionDeclarationTokens() {
    return orSets(PythonDialectsTokenSetContributor::getFunctionDeclarationTokens);
  }

  /**
   * Returns all element types of Python dialects that can be used as unbalanced braces recovery tokens in the lexer.
   */
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return orSets(PythonDialectsTokenSetContributor::getUnbalancedBracesRecoveryTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
   */
  public TokenSet getReferenceExpressionTokens() {
    return orSets(PythonDialectsTokenSetContributor::getReferenceExpressionTokens);
  }

  private static @NotNull TokenSet orSets(@NotNull Function<PythonDialectsTokenSetContributor, TokenSet> getter) {
    return TokenSet.orSet(
      StreamEx
        .of(PythonDialectsTokenSetContributor.EP_NAME.getExtensionList())
        .map(getter)
        .toArray(TokenSet.class)
    );
  }
}
