/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.rename.RenameProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class RenameRefactoringImpl extends RefactoringImpl<RenameProcessor> implements RenameRefactoring {

  public RenameRefactoringImpl(Project project,
                               PsiElement element,
                               String newName,
                               boolean toSearchInComments,
                               boolean toSearchInNonJavaFiles) {
    super(new RenameProcessor(project, element, newName, toSearchInComments, toSearchInNonJavaFiles, true));
  }

  public void addElement(PsiElement element, String newName) {
    myProcessor.addElement(element, newName);
  }

  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }

  public List<String> getNewNames() {
    return myProcessor.getNewNames();
  }

  public void setShouldRenameVariables(boolean value) {
    myProcessor.setShouldRenameVariables(value);
  }

  public void setShouldRenameInheritors(boolean value) {
    myProcessor.setShouldRenameInheritors(value);
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
