// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public final class PyDoctestTokenSetContributor extends PythonDialectsTokenSetContributorBase {
  public static final TokenSet DOCTEST_REFERENCE_EXPRESSIONS = TokenSet.create(PyDoctestTokenTypes.DOC_REFERENCE);

  @Override
  public @NotNull TokenSet getExpressionTokens() {
    return DOCTEST_REFERENCE_EXPRESSIONS;
  }

  @Override
  public @NotNull TokenSet getReferenceExpressionTokens() {
    return DOCTEST_REFERENCE_EXPRESSIONS;
  }
}
