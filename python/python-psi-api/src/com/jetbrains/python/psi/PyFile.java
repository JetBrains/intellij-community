// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyFile extends PyAstFile, PyElement, PsiFile, PyDocStringOwner, ScopeOwner {
  @Override
  default List<PyStatement> getStatements() {
    //noinspection unchecked
    return (List<PyStatement>)PyAstFile.super.getStatements();
  }

  @NotNull
  List<PyClass> getTopLevelClasses();

  @NotNull
  List<PyFunction> getTopLevelFunctions();

  List<PyTargetExpression> getTopLevelAttributes();

  @Nullable
  PyFunction findTopLevelFunction(@NotNull String name);

  @Nullable
  PyClass findTopLevelClass(@NonNls @NotNull String name);

  @Nullable
  PyTargetExpression findTopLevelAttribute(@NotNull String name);

  @NotNull
  List<PyTypeAliasStatement> getTypeAliasStatements();

  @Nullable
  PyTypeAliasStatement findTypeAliasStatement(@NotNull String name);

  /**
   * Return the list of all 'from ... import' statements in the top-level scope of the file.
   *
   * @return the list of 'from ... import' statements.
   */
  @NotNull
  List<PyFromImportStatement> getFromImports();

  /**
   * Return an exported PSI element defined in the file with the given name.
   */
  @Nullable
  PsiElement findExportedName(String name);

  /**
   * Iterate over exported PSI elements defined in the file.
   */
  @NotNull
  Iterable<PyElement> iterateNames();

  /**
   * Return the resolved exported elements.
   */
  @NotNull
  List<RatedResolveResult> multiResolveName(@NotNull String name);

  /**
   * Return the resolved elements.
   *
   * @param exported found element must be exported, i.e. visible from other modules.
   */
  @NotNull
  List<RatedResolveResult> multiResolveName(@NotNull String name, boolean exported);

  /**
   * @deprecated Use {@link #multiResolveName(String)} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  PsiElement getElementNamed(String name);

  /**
   * Returns the list of import elements in all 'import xxx' statements in the top-level scope of the file.
   *
   * @return the list of import targets.
   */
  @NotNull
  List<PyImportElement> getImportTargets();

  /**
   * Returns the list of names in the __all__ declaration, or null if there is no such declaration in the module.
   *
   * @return the list of names or null.
   */
  @Nullable
  List<String> getDunderAll();

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning.
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  String getDeprecationMessage();

  /**
   * Returns the sequential list of import statements in the beginning of the file.
   */
  List<PyImportStatementBase> getImportBlock();

  @Override
  default @Nullable PyStringLiteralExpression getDocStringExpression() {
    return (PyStringLiteralExpression)PyAstFile.super.getDocStringExpression();
  }
}
