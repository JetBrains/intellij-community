package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Belyaev
 */
public final class ClasspathEntryMacro extends Macro {
  public String getName() {
    return "ClasspathEntry";
  }

  public String getDescription() {
    return "Entry in the classpath the element belongs to";
  }

  public String expand(final DataContext dataContext) {
    final Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    final VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (file == null) return null;
    final VirtualFile classRoot = ProjectRootManager.getInstance(project).getFileIndex().getClassRootForFile(file);
    if (classRoot == null) return null;
    return getPath(classRoot);
  }
}