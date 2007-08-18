
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;

public final class ProjectPathMacro extends Macro {
  public String getName() {
    return "Projectpath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.source.path");
  }

  public String expand(DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    return ProjectRootsTraversing.collectRoots(project, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString();
  }
}
