// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTypeAliasStatement;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import org.jetbrains.annotations.Nullable;

/**
 * Represents Type Alias Statement added in <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeAliasStatement extends PyAstTypeAliasStatement, PyStatement, PsiNameIdentifierOwner, PyTypeParameterListOwner, PyTypedElement,
                                              StubBasedPsiElement<PyTypeAliasStatementStub>, PyQualifiedNameOwner, ScopeOwner {

  @Override
  @Nullable
  default PyExpression getTypeExpression() {
    return (PyExpression)PyAstTypeAliasStatement.super.getTypeExpression();
  }

  /**
   * Returns right-hand side of the type alias statement as text.
   * <p>
   * The text is taken from stub if the stub is presented.
   */
  @Nullable
  String getTypeExpressionText();

  @Override
  @Nullable
  default PyTypeParameterList getTypeParameterList() {
    return (PyTypeParameterList)PyAstTypeAliasStatement.super.getTypeParameterList();
  }
}
