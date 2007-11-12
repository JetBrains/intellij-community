package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;
import com.intellij.openapi.actionSystem.DataContext;

public final class ProjectFilePathMacro extends Macro {
  public String getName() {
    return "ProjectFilePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.file.path");
  }

  public String expand(DataContext dataContext) {
    return DataAccessors.PROJECT_FILE_PATH.from(dataContext);
  }
}