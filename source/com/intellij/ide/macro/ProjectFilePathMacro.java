package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ide.IdeBundle;

public final class ProjectFilePathMacro extends Macro {
  public String getName() {
    return "ProjectFilePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.file.path");
  }

  public String expand(DataContext dataContext) {
    return DataAccessor.PROJECT_FILE_PATH.from(dataContext);
  }
}