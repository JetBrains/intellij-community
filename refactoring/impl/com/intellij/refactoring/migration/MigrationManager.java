package com.intellij.refactoring.migration;

import com.intellij.openapi.project.Project;

public class MigrationManager {
  private final Project myProject;
  private final MigrationMapSet myMigrationMapSet = new MigrationMapSet();

  public MigrationManager(Project project) {
    myProject = project;
  }

  public void showMigrationDialog() {
    final MigrationDialog migrationDialog = new MigrationDialog(myProject, myMigrationMapSet);
    migrationDialog.show();
    if (!migrationDialog.isOK()) {
      return;
    }
    MigrationMap migrationMap = migrationDialog.getMigrationMap();
    if (migrationMap == null) return;

    new MigrationProcessor(myProject, migrationMap).run();
  }
}
