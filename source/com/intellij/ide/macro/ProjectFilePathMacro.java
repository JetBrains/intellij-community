package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

public final class ProjectFilePathMacro extends Macro {
  public String getName() {
    return "ProjectFilePath";
  }

  public String getDescription() {
    return "The path of the project file";
  }

  public String expand(DataContext dataContext) {
    return DataAccessor.PROJECT_FILE_PATH.from(dataContext);
  }
}