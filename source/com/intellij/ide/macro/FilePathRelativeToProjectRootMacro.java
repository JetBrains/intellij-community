package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class FilePathRelativeToProjectRootMacro extends Macro {
  public String getName() {
    return "FilePathRelativeToProjectRoot";
  }

  public String getDescription() {
    return "File path relative to the module content root the file belongs to";
  }

  public String expand(final DataContext dataContext) {
    final Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null) return null;
    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
    if (contentRoot == null) return null;
    return FileUtil.getRelativePath(getIOFile(contentRoot), getIOFile(file));
  }
}
