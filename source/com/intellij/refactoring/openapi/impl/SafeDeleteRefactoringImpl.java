/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.SafeDeleteRefactoring;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
public class SafeDeleteRefactoringImpl extends RefactoringImpl<SafeDeleteProcessor> implements SafeDeleteRefactoring {
  SafeDeleteRefactoringImpl(Project project, PsiElement[] elements) {
    super(SafeDeleteProcessor.createInstance(project, BaseRefactoringProcessor.EMPTY_CALLBACK, elements, true, true));
  }

  public List<PsiElement> getElements() {
    final PsiElement[] elements = myProcessor.getElements();
    return Collections.unmodifiableList(Arrays.asList(elements));
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInCommentsAndStrings();
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInCommentsAndStrings(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchNonJava(value);
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchNonJava();
  }
}
