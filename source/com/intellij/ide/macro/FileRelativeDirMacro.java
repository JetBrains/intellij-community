
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class FileRelativeDirMacro extends Macro {
  public String getName() {
    return "FileRelativeDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.directory.relative");
  }

  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) return null;
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) return null;
    //if (!file.isDirectory()){
    //  file = file.getParent();
    //  if (file == null) return null;
    //}
    final VirtualFile baseDir = DataAccessors.PROJECT_BASE_DIR.from(dataContext);
    if (baseDir == null) {
      return null;
    }

    VirtualFile dir = DataAccessors.VIRTUAL_DIR_OR_PARENT.from(dataContext);
    if (dir == null) return null;
    return FileUtil.getRelativePath(VfsUtil.virtualToIoFile(baseDir), VfsUtil.virtualToIoFile(dir));
  }
}
