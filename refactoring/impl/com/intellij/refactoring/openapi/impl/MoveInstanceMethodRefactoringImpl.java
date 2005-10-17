/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.MoveInstanceMethodRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;

/**
 * @author ven
 */
public class MoveInstanceMethodRefactoringImpl extends RefactoringImpl<MoveInstanceMethodProcessor> implements MoveInstanceMethodRefactoring {
  MoveInstanceMethodRefactoringImpl(Project project, PsiMethod method, PsiVariable targetVariable) {
    super(new MoveInstanceMethodProcessor(project, method, targetVariable, null, MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)));
  }

  public PsiMethod getMethod() {
    return myProcessor.getMethod();
  }

  public PsiVariable getTargetVariable() {
    return myProcessor.getTargetVariable();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }
}
