/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class MoveClassesOrPackagesRefactoringImpl extends RefactoringImpl<MoveClassesOrPackagesProcessor> implements MoveClassesOrPackagesRefactoring {


  public MoveClassesOrPackagesRefactoringImpl(Project project, PsiElement[] elements, MoveDestination moveDestination) {
    super(new MoveClassesOrPackagesProcessor(project, elements, moveDestination, true, true, null));
  }

  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }

  public PackageWrapper getTargetPackage() {
    return myProcessor.getTargetPackage();
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchInNonJavaFiles(value);
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchInNonJavaFiles();
  }
}
