package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Belyaev
 */
public final class SourcepathEntryMacro extends Macro {
  public String getName() {
    return "SourcepathEntry";
  }

  public String getDescription() {
    return "Entry in the sourcepath the element belongs to";
  }

  public String expand(final DataContext dataContext) {
    final Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null) return null;
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file);
    if (sourceRoot == null) return null;
    return getPath(sourceRoot);
  }
}