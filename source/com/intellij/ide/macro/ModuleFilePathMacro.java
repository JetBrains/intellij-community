package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

public final class ModuleFilePathMacro extends Macro {
  public String getName() {
    return "ModuleFilePath";
  }

  public String getDescription() {
    return "The path of the module file";
  }

  public String expand(DataContext dataContext) {
    return DataAccessor.MODULE_FILE_PATH.from(dataContext);
  }
}