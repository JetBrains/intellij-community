/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.ReplaceConstructorWithFactoryRefactoring;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryRefactoringImpl extends RefactoringImpl<ReplaceConstructorWithFactoryProcessor> implements ReplaceConstructorWithFactoryRefactoring {
  ReplaceConstructorWithFactoryRefactoringImpl(Project project, PsiMethod method, PsiClass targetClass, String factoryName) {
    super(new ReplaceConstructorWithFactoryProcessor(project, method, null, targetClass, factoryName));
  }

  ReplaceConstructorWithFactoryRefactoringImpl(Project project, PsiClass originalClass, PsiClass targetClass, String factoryName) {
    super(new ReplaceConstructorWithFactoryProcessor(project, null, originalClass, targetClass, factoryName));
  }

  public PsiClass getOriginalClass() {
    return myProcessor.getOriginalClass();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }

  public PsiMethod getConstructor() {
    return myProcessor.getConstructor();
  }

  public String getFactoryName() {
    return myProcessor.getFactoryName();
  }

}
