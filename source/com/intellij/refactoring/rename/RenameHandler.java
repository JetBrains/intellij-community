package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 * @author dsl
 */
public interface RenameHandler extends RefactoringActionHandler {
  // called during rename action update. should not perform any user interactions
  boolean isAvailableOnDataContext(DataContext dataContext);
  // called on rename actionPeformed. Can obtain additional info from user
  boolean isRenaming(DataContext dataContext);
}
