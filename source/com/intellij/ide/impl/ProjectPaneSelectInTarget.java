package com.intellij.ide.impl;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;

public class ProjectPaneSelectInTarget extends ProjectViewSelectInTarget {
  public ProjectPaneSelectInTarget(Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.PROJECT;
  }

  public boolean canSelect(PsiFileSystemItem file) {
    final VirtualFile vFile = file.getVirtualFile();
    return canSelect(vFile);
  }

  public boolean isSubIdSelectable(String subId, VirtualFile file) {
    return canSelect(file);
  }

  private boolean canSelect(final VirtualFile vFile) {
    if (vFile != null && vFile.isValid()) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      if (projectFileIndex.getModuleForFile(vFile) != null) {
        return true;
      }

      if (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile)) {
        return true;
      }
    }

    return false;
  }

  public String getMinorViewId() {
    return ProjectViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.PROJECT_WEIGHT;
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }
}
