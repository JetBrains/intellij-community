
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;

public class FileRelativeDirMacro extends Macro {
  public String getName() {
    return "FileRelativeDir";
  }

  public String getDescription() {
    return "File directory relative to the project file";
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
    String path = DataAccessor.PROJECT_FILE_PATH.from(dataContext);
    if (path == null) return null;
    VirtualFile dir = DataAccessor.VIRTUAL_DIR_OR_PARENT.from(dataContext);
    if (dir == null) return null;
    return FileUtil.getRelativePath(new File(path), VfsUtil.virtualToIoFile(dir));
  }
}
