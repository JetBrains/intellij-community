
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.vfs.VirtualFile;

public class FileNameMacro extends Macro {
  public String getName() {
    return "FileName";
  }

  public String getDescription() {
    return "File name";
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null) return null;
    return file.getName();
  }
}
