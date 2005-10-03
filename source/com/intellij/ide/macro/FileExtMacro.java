
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.IdeBundle;

public final class FileExtMacro extends Macro {
  public String getName() {
    return "FileExt";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.extension");
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null ) return null;
    return file.getExtension();
  }
}
