package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;

/**
 *  @author dsl
 */
public class SingleSourceRootMoveDestination implements MoveDestination {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination");
  private final PackageWrapper myPackage;
  private final PsiDirectory myTagretDirectory;

  public SingleSourceRootMoveDestination(PackageWrapper aPackage, PsiDirectory tagretDirectory) {
    LOG.assertTrue(aPackage.equalToPackage(tagretDirectory.getPackage()));
    myPackage = aPackage;
    myTagretDirectory = tagretDirectory;
  }

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return myTagretDirectory;
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return myTagretDirectory;
  }

  public PsiDirectory getTargetDirectory(PsiDirectory source) {
    return myTagretDirectory;
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
                                     ArrayList<String> conflicts) {
    RefactoringUtil.analyzeModuleConflicts(myPackage.getManager().getProject(), elements, myTagretDirectory, conflicts);
  }

  public PsiDirectory getTargetDirectory(PsiFile source) {
    return myTagretDirectory;
  }
}
