package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public final class ProjectNameMacro extends Macro {
  public String getName() {
    return "ProjectName";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.file.name");
  }

  public String expand(DataContext dataContext) {
    final Project project = DataAccessors.PROJECT.from(dataContext);
    if (project == null) {
      return null;
    }
    return project.getName();
  }
}