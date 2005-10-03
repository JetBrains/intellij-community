package com.intellij.ide.impl;

import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class PackageViewSelectInTarget extends ProjectViewSelectInTarget {
  public PackageViewSelectInTarget(Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.PACKAGES;
  }

  public boolean canSelect(PsiFile file) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile vFile = getCorrespondingVirtualFile(file);
    
    if (vFile == null) {
      return false;
    }

    return fileIndex.isInSourceContent(vFile) || isInLibraryContentOnly(vFile);
  }

 /* public void select(PsiElement element, boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    if (!projectView.isShowLibraryContents(getMinorViewId())) {
      if (isInLibraryContentOnly(getCorrespondingVirtualFile(element))) {
        projectView.setShowLibraryContents(true, getMinorViewId()); // turn the filter on in order to show the file in the view
      }
    }
    super.select(element, requestFocus);
  }*/

  private boolean isInLibraryContentOnly(final VirtualFile vFile) {
    if (vFile == null) {
      return false;
    }
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    return (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile)) && !projectFileIndex.isInSourceContent(vFile);
  }

  private VirtualFile getCorrespondingVirtualFile(PsiElement psiElement) {
    final VirtualFile vFile;
    if (psiElement instanceof PsiFile) {
      vFile = ((PsiFile)psiElement).getVirtualFile();
    }
    else {
      final PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile == null) {
        vFile = null;
      }
      else {
        vFile = psiFile.getVirtualFile();
      }
    }
    if (vFile == null || !vFile.isValid()) {
      return null;
    }
    return vFile;
  }

  public String getMinorViewId() {
    return PackageViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.PACKAGES_WEIGHT;
  }
}
