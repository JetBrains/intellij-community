
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.module.Module;

public final class ModulePathMacro extends Macro {
  public String getName() {
    return "ModuleSourcePath";
  }

  public String getDescription() {
    return "Module source path";
  }

  public String expand(DataContext dataContext) {
    final Module module = DataAccessor.MODULE.from(dataContext);
    if (module == null) {
      return null;
    }
    return ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString();
  }
}
