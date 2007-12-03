package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiPatchBaseDirectoryDetector extends PatchBaseDirectoryDetector {
  private Project myProject;

  public PsiPatchBaseDirectoryDetector(final Project project) {
    myProject = project;
  }

  @Nullable
  public Result detectBaseDirectory(final String patchFileName) {
    String[] nameComponents = patchFileName.split("/");
    final String patchName = nameComponents[nameComponents.length - 1];
    final PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(patchName);
    if (psiFiles.length == 1) {
      PsiDirectory parent = psiFiles [0].getContainingDirectory();
      for(int i=nameComponents.length-2; i >= 0; i--) {
        if (!parent.getName().equals(nameComponents [i]) || parent.getVirtualFile() == myProject.getBaseDir()) {
          return new Result(parent.getVirtualFile().getPresentableUrl(), i+1);
        }
        parent = parent.getParentDirectory();
      }
      if (parent == null) return null;
      return new Result(parent.getVirtualFile().getPresentableUrl(), 0);
    }
    return null;
  }
}