package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

/**
 *  @author dsl
 */
public abstract class AutocreatingMoveDestination implements MoveDestination {
  protected final PackageWrapper myPackage;
  protected final PsiManager myManager;
  protected final ProjectFileIndex myFileIndex;

  public AutocreatingMoveDestination(PackageWrapper targetPackage) {
    myPackage = targetPackage;
    myManager = myPackage.getManager();
    myFileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
  }

  public abstract PackageWrapper getTargetPackage();

  public abstract PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException;

  public abstract PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException;

  protected String checkCanCreateInSourceRoot(final VirtualFile targetSourceRoot) {
    final String targetQName = myPackage.getQualifiedName();
    final String sourceRootPackage = myFileIndex.getPackageNameByDirectory(targetSourceRoot);
    if (!RefactoringUtil.canCreateInSourceRoot(sourceRootPackage, targetQName)) {
      return RefactoringBundle.message("source.folder.0.has.package.prefix.1", targetSourceRoot.getPresentableUrl(),
                                       sourceRootPackage, targetQName);
    }
    return null;
  }
}
