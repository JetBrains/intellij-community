package com.intellij.ide.impl;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;

public class PackagesPaneSelectInTarget extends ProjectViewSelectInTarget {
  public PackagesPaneSelectInTarget(Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.PACKAGES;
  }

  public boolean canSelect(PsiFile file) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile vFile = PsiUtil.getVirtualFile(file);

    if (vFile == null || !vFile.isValid()) {
      return false;
    }

    return fileIndex.isInSourceContent(vFile) || isInLibraryContentOnly(vFile);
  }

  private boolean isInLibraryContentOnly(final VirtualFile vFile) {
    if (vFile == null) {
      return false;
    }
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    return (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile)) && !projectFileIndex.isInSourceContent(vFile);
  }

  public String getMinorViewId() {
    return PackageViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.PACKAGES_WEIGHT;
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }
}
