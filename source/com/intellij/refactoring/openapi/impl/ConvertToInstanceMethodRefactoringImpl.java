/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.ConvertToInstanceMethodRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodProcessor;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodRefactoringImpl extends RefactoringImpl<ConvertToInstanceMethodProcessor> implements ConvertToInstanceMethodRefactoring {
  ConvertToInstanceMethodRefactoringImpl(Project project, PsiMethod method, PsiParameter targetParameter) {
    super(new ConvertToInstanceMethodProcessor(project, method, targetParameter, null));
  }

  public PsiMethod getMethod() {
    return myProcessor.getMethod();
  }

  public PsiParameter getTargetParameter() {
    return myProcessor.getTargetParameter();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }
}
