package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 * @author dsl
 */
public interface RenameHandler extends RefactoringActionHandler {
  boolean isAvailableOnDataContext(DataContext dataContext);
}
