package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

import java.io.File;

public final class ProjectNameMacro extends Macro {
  public String getName() {
    return "ProjectName";
  }

  public String getDescription() {
    return "The name of the project file without extension";
  }

  public String expand(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    String path = project.getProjectFilePath();
    File file = new File(path);
    if (!file.exists() || !file.isFile()) return null;
    String name = file.getName();
    int index = name.lastIndexOf('.');
    return index >= 0 ? name.substring(0, index) : name;
  }
}