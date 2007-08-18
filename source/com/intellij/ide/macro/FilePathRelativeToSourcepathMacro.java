package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class FilePathRelativeToSourcepathMacro extends Macro {
  public String getName() {
    return "FilePathRelativeToSourcepath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.path.relative.to.sourcepath.root");
  }

  public String expand(final DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (file == null) return null;
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file);
    if (sourceRoot == null) return null;
    return FileUtil.getRelativePath(getIOFile(sourceRoot), getIOFile(file));
  }
}
