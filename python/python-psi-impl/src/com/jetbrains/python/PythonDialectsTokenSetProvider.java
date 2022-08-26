// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides element types of various kinds for known Python dialects.
 *
 * @author vlan
 */
@Service
public final class PythonDialectsTokenSetProvider implements Disposable {

  @NotNull
  private final ConcurrentHashMap<String, TokenSet> myCache = new ConcurrentHashMap<>(7); // number of token types

  public PythonDialectsTokenSetProvider() {
    PythonDialectsTokenSetContributor.EP_NAME.addChangeListener(myCache::clear, this);
  }

  @NotNull
  public static PythonDialectsTokenSetProvider getInstance() {
    return ApplicationManager.getApplication().getService(PythonDialectsTokenSetProvider.class);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
   */
  public TokenSet getStatementTokens() {
    return getTokenSet("statement", PythonDialectsTokenSetContributor::getStatementTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
   */
  public TokenSet getExpressionTokens() {
    return getTokenSet("expression", PythonDialectsTokenSetContributor::getExpressionTokens);
  }

  /**
   * Returns all element types of Python dialects that are language keywords.
   */
  public TokenSet getKeywordTokens() {
    return getTokenSet("keyword", PythonDialectsTokenSetContributor::getKeywordTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
   */
  public TokenSet getParameterTokens() {
    return getTokenSet("parameter", PythonDialectsTokenSetContributor::getParameterTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
   */
  public TokenSet getFunctionDeclarationTokens() {
    return getTokenSet("functionDeclaration", PythonDialectsTokenSetContributor::getFunctionDeclarationTokens);
  }

  /**
   * Returns all element types of Python dialects that can be used as unbalanced braces recovery tokens in the lexer.
   */
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return getTokenSet("unbalancedBracesRecovery", PythonDialectsTokenSetContributor::getUnbalancedBracesRecoveryTokens);
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
   */
  public TokenSet getReferenceExpressionTokens() {
    return getTokenSet("referenceExpression", PythonDialectsTokenSetContributor::getReferenceExpressionTokens);
  }

  @Override
  public void dispose() {
    myCache.clear();
  }

  private @NotNull TokenSet getTokenSet(@NotNull String key, @NotNull Function<? super PythonDialectsTokenSetContributor, ? extends TokenSet> getter) {
    return myCache.computeIfAbsent(key, __ -> orSets(getter));
  }

  private static @NotNull TokenSet orSets(@NotNull Function<? super PythonDialectsTokenSetContributor, ? extends TokenSet> getter) {
    return TokenSet.orSet(
        ContainerUtil.map2Array(PythonDialectsTokenSetContributor.EP_NAME.getExtensionList(), TokenSet.class, getter)
    );
  }
}
