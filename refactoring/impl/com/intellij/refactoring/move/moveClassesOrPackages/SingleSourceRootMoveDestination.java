package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;
import java.util.Collection;

/**
 *  @author dsl
 */
public class SingleSourceRootMoveDestination implements MoveDestination {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination");
  private final PackageWrapper myPackage;
  private final PsiDirectory myTargetDirectory;

  public SingleSourceRootMoveDestination(PackageWrapper aPackage, PsiDirectory targetDirectory) {
    LOG.assertTrue(aPackage.equalToPackage(JavaDirectoryService.getInstance().getPackage(targetDirectory)));
    myPackage = aPackage;
    myTargetDirectory = targetDirectory;
  }

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return myTargetDirectory;
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return myTargetDirectory;
  }

  public PsiDirectory getTargetDirectory(PsiDirectory source) {
    return myTargetDirectory;
  }

  public String verify(PsiFile source) {
    return null;
  }

  public String verify(PsiDirectory source) {
    return null;
  }

  public String verify(PsiPackage source) {
    return null;
  }

  public void analyzeModuleConflicts(final Collection<PsiElement> elements,
                                     ArrayList<String> conflicts, final UsageInfo[] usages) {
    RefactoringUtil.analyzeModuleConflicts(myPackage.getManager().getProject(), elements, usages, myTargetDirectory, conflicts);
  }

  public PsiDirectory getTargetDirectory(PsiFile source) {
    return myTargetDirectory;
  }
}
