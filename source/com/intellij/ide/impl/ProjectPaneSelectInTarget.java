package com.intellij.ide.impl;

import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

public class ProjectPaneSelectInTarget extends ProjectViewSelectInTarget {
  public ProjectPaneSelectInTarget(Project project) {
    super(project);
  }

  public String toString() {
    return "Project";
  }

  public boolean canSelect(PsiFile file) {
    if (file.getManager().isInProject(file)) {
      return true;
    }

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile != null && vFile.isValid()) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
      if (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile)) {
        return true;
      }
    }

    return false;
  }

  public String getMinorViewId() {
    return ProjectViewPane.ID;
  }
}
