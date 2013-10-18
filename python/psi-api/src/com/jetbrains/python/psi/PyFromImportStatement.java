package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes "from ... import" statements.
 */
public interface PyFromImportStatement extends PyImportStatementBase, StubBasedPsiElement<PyFromImportStatementStub> {
  boolean isStarImport();

  /**
   * Returns a reference the module from which import is required.
   * @return reference to module. If the 'from' reference is relative and consists entirely of dots, null is returned.
   */
  @Nullable PyReferenceExpression getImportSource();

  @Nullable
  QualifiedName getImportSourceQName();

  /**
   * @return the star in "from ... import *"
   */
  @Nullable PyStarImportElement getStarImportElement();

  /**
   * @return number of dots in relative "from" clause, or 0 in absolute import.
   */
  int getRelativeLevel();

  /**
   * @return true iff the statement is an import from __future__.
   */
  boolean isFromFuture();

  /**
   * If the from ... import statement uses an import list in parentheses, returns the opening parenthesis.
   *
   * @return opening parenthesis token or null
   */
  @Nullable
  PsiElement getLeftParen();

  /**
   * If the from ... import statement uses an import list in parentheses, returns the closing parenthesis.
   *
   * @return closing parenthesis token or null
   */
  @Nullable
  PsiElement getRightParen();

  /**
   * Resolves the import source qualified name to a file or directory. Note: performs a Python only resolve,
   * doesn't handle extension points such as import from Java classes.
   *
   * @return the resolved import source (file or directory containing __init__.py), or null if the import is unresolved.
   */
  @Nullable
  PsiElement resolveImportSource();

  /**
   * Resolves the import source qualified name to a number of possible files or directories. Note: performs a Python only resolve,
   * doesn't handle extension points such as import from Java classes.
   *
   * @return possible candidates the resolved import source (file or directory containing __init__.py), or an empty list if the import is unresolved.
   */
  @NotNull
  List<PsiElement> resolveImportSourceCandidates();
}
