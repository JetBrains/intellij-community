package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public final class ProjectFileDirMacro extends Macro {
  public String getName() {
    return "ProjectFileDir";
  }

  public String getDescription() {
    return "The directory of the project file";
  }

  public String expand(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    VirtualFile projectFile = project.getProjectFile();
    if (projectFile == null) {
      return null;
    }
    VirtualFile dir = projectFile.getParent();
    return dir.getPath().replace('/',File.separatorChar);
  }
}