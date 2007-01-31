package com.intellij.refactoring;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationManager;
import com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog;

public class RefactoringManager {
  private MigrationManager myMigrateManager;
  private TypeMigrationDialog myMigrationDialog;

  public static RefactoringManager getInstance(Project project) {
    return ServiceManager.getService(project, RefactoringManager.class);
  }

  public RefactoringManager(Project project) {
    myMigrateManager = new MigrationManager(project);
    myMigrationDialog = null;
  }

  public MigrationManager getMigrateManager() {
    return myMigrateManager;
  }

  public void startTypeMigrationSession(final TypeMigrationDialog d){
    myMigrationDialog = d;
  }

  public TypeMigrationDialog getTypeMigrationDialog (){
    return myMigrationDialog;
  }

  public void endTypeMigrationSession(){
    myMigrationDialog = null;
  }
}