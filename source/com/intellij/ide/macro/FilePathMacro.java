
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.vfs.VirtualFile;

public final class FilePathMacro extends Macro {
  public String getName() {
    return "FilePath";
  }

  public String getDescription() {
    return "File path";
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null) return null;
    return getPath(file);
  }
}
