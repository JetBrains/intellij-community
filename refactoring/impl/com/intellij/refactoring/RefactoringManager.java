package com.intellij.refactoring;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationManager;

public class RefactoringManager {
  private MigrationManager myMigrateManager;

  public static RefactoringManager getInstance(Project project) {
    return ServiceManager.getService(project, RefactoringManager.class);
  }

  public RefactoringManager(Project project) {
    myMigrateManager = new MigrationManager(project);
  }

  public MigrationManager getMigrateManager() {
    return myMigrateManager;
  }

}
