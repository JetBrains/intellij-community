package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;

/**
 *  @author dsl
 */
public class AutocreatingSingleSourceRootMoveDestination extends AutocreatingMoveDestination {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination");
  private final VirtualFile mySourceRoot;

  public AutocreatingSingleSourceRootMoveDestination(PackageWrapper targetPackage, VirtualFile sourceRoot) {
    super(targetPackage);
    mySourceRoot = sourceRoot;
  }

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  public PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException {
    return getDirectory();
  }

  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getDirectory();
  }

  public String verify(PsiFile source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public String verify(PsiDirectory source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public String verify(PsiPackage aPackage) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public void analyzeModuleConflicts(final Collection<PsiElement> elements,
                                     ArrayList<String> conflicts) {
    RefactoringUtil.analyzeModuleConflicts(getTargetPackage().getManager().getProject(), elements, mySourceRoot, conflicts);
  }

  PsiDirectory myTargetDirectory = null;
  private PsiDirectory getDirectory() throws IncorrectOperationException {
    if (myTargetDirectory == null) {
      myTargetDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
    }
    return RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }
}
