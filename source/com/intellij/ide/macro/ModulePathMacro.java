
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.module.Module;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public final class ModulePathMacro extends Macro {
  public String getName() {
    return "ModuleSourcePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.module.source.path");
  }

  public String expand(DataContext dataContext) {
    final Module module = DataAccessors.MODULE.from(dataContext);
    if (module == null) {
      return null;
    }
    return ProjectRootsTraversing.collectRoots(module, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString();
  }
}
