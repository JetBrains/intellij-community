package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ide.IdeBundle;

import java.io.File;

public final class ProjectFileDirMacro extends Macro {
  public String getName() {
    return "ProjectFileDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.file.directory");
  }

  public String expand(DataContext dataContext) {
    final String path = DataAccessor.PROJECT_FILE_PATH.from(dataContext);
    if (path == null) {
      return null;
    }
    final File fileDir = new File(path).getParentFile();
    if (fileDir == null) {
      return null;
    }
    return fileDir.getPath();
  }
}