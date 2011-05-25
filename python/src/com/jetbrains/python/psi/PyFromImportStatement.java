package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.Nullable;

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
  PyQualifiedName getImportSourceQName();

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
}
