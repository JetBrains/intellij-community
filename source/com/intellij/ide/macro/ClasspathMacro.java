
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;

public final class ClasspathMacro extends Macro {
  public String getName() {
    return "Classpath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.classpath");
  }

  public String expand(DataContext dataContext) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    return ProjectRootsTraversing.collectRoots(project, ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();
  }
}
