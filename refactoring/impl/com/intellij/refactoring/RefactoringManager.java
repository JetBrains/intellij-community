package com.intellij.refactoring;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationManager;
import com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog;

public class RefactoringManager implements ProjectComponent {
  private MigrationManager myMigrateManager;
  private TypeMigrationDialog myMigrationDialog;

  public static RefactoringManager getInstance(Project project) {
    return project.getComponent(RefactoringManager.class);
  }

  public RefactoringManager(Project project) {
    myMigrateManager = new MigrationManager(project);
    myMigrationDialog = null;
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public MigrationManager getMigrateManager() {
    return myMigrateManager;
  }

  public String getComponentName() {
    return "RefactoringManager";
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