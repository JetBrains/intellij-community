package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;

public final class ModuleNameMacro extends Macro {
  public String getName() {
    return "ModuleName";
  }

  public String getDescription() {
    return "The name of the module file without extension";
  }

  public String expand(DataContext dataContext) {
    final Module module = DataAccessor.MODULE.from(dataContext);
    if (module == null) {
      return null;
    }
    return module.getName();
  }
}