
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;

public class FileRelativePathMacro extends Macro {
  public String getName() {
    return "FileRelativePath";
  }

  public String getDescription() {
    return "File path relative to the project file";
  }

  public String expand(DataContext dataContext) {
    String path = DataAccessor.PROJECT_FILE_PATH.from(dataContext);
    if (path == null) return null;
    VirtualFile file = DataAccessor.VIRTUAL_FILE.from(dataContext);
    if (file == null) return null;
    return FileUtil.getRelativePath(new File(path), VfsUtil.virtualToIoFile(file));
  }
}
