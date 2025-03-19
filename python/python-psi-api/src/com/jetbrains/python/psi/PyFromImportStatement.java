// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstFromImportStatement;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes "from ... import" statements.
 */
public interface PyFromImportStatement extends PyAstFromImportStatement, PyImportStatementBase, StubBasedPsiElement<PyFromImportStatementStub>,
                                               PyImplicitImportNameDefiner {
  /**
   * Returns a reference the module from which import is required.
   * @return reference to module. If the 'from' reference is relative and consists entirely of dots, null is returned.
   */
  @Override
  default @Nullable PyReferenceExpression getImportSource() {
    return (PyReferenceExpression)PyAstFromImportStatement.super.getImportSource();
  }

  /**
   * @return the star in "from ... import *"
   */
  @Override
  @Nullable
  PyStarImportElement getStarImportElement();

  /**
   * Resolves the import source qualified name to a file or directory. Note: performs a Python only resolve,
   * doesn't handle extension points such as import from Java classes.
   *
   * @return the resolved import source (file or directory containing __init__.py), or null if the import is unresolved.
   */
  @Nullable
  PsiFileSystemItem resolveImportSource();

  /**
   * Resolves the import source qualified name to a number of possible files or directories. Note: performs a Python only resolve,
   * doesn't handle extension points such as import from Java classes.
   *
   * @return possible candidates the resolved import source (file or directory containing __init__.py), or an empty list if the import is unresolved.
   */
  @NotNull
  List<PsiElement> resolveImportSourceCandidates();
}
