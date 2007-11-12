
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public final class FileDirMacro extends Macro {
  public String getName() {
    return "FileDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.directory");
  }

  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) return null;
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) return null;
    //if (!file.isDirectory()) {
    //  file = file.getParent();
    //  if (file == null) return null;
    //}
    VirtualFile dir = DataAccessors.VIRTUAL_DIR_OR_PARENT.from(dataContext);
    if (dir == null) return null;
    return getPath(dir);
  }
}
