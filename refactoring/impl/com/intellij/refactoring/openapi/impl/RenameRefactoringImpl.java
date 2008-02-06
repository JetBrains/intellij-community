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
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamerFactory;
import com.intellij.refactoring.rename.naming.AutomaticInheritorRenamerFactory;

import java.util.Set;
import java.util.Collection;

/**
 * @author dsl
 */
public class RenameRefactoringImpl extends RefactoringImpl<RenameProcessor> implements RenameRefactoring {
  private static final AutomaticVariableRenamerFactory ourVariableRenamerFactory = new AutomaticVariableRenamerFactory();
  private static final AutomaticInheritorRenamerFactory ourInheritorRenamerFactory = new AutomaticInheritorRenamerFactory();

  public RenameRefactoringImpl(Project project,
                               PsiElement element,
                               String newName,
                               boolean toSearchInComments,
                               boolean toSearchInNonJavaFiles) {
    super(new RenameProcessor(project, element, newName, toSearchInComments, toSearchInNonJavaFiles));
  }

  public void addElement(PsiElement element, String newName) {
    myProcessor.addElement(element, newName);
  }

  public Set<PsiElement> getElements() {
    return myProcessor.getElements();
  }

  public Collection<String> getNewNames() {
    return myProcessor.getNewNames();
  }

  public void setShouldRenameVariables(boolean value) {
    if (value) {
      myProcessor.addRenamerFactory(ourVariableRenamerFactory);
    }
    else {
      myProcessor.removeRenamerFactory(ourVariableRenamerFactory);
    }
  }

  public void setShouldRenameInheritors(boolean value) {
    if (value) {
      myProcessor.addRenamerFactory(ourInheritorRenamerFactory);
    }
    else {
      myProcessor.removeRenamerFactory(ourInheritorRenamerFactory);
    }
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchTextOccurrences(value);
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchTextOccurrences();
  }
}
