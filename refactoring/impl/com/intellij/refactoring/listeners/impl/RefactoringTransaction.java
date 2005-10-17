package com.intellij.refactoring.listeners.impl;

import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;

/**
 * @author dsl
 */
public interface RefactoringTransaction {
  /**
   * Returns listener for element (element must belong to set of affected elements).
   * Refactorings should call appropriate methods of a listener, giving a modified (or new) element.
   * @param element
   * @return
   */
  RefactoringElementListener getElementListener(PsiElement element);

  /**
   *
   */
  void commit();
}
