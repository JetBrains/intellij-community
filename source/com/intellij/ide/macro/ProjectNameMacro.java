package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

import java.io.File;

public final class ProjectNameMacro extends Macro {
  public String getName() {
    return "ProjectName";
  }

  public String getDescription() {
    return "The name of the project file without extension";
  }

  public String expand(DataContext dataContext) {
    final Project project = DataAccessor.PROJECT.from(dataContext);
    if (project == null) {
      return null;
    }
    return project.getName();
  }
}