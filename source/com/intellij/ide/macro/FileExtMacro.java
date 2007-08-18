
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;

public final class FileExtMacro extends Macro {
  public String getName() {
    return "FileExt";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.extension");
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (file == null ) return null;
    return file.getExtension();
  }
}
