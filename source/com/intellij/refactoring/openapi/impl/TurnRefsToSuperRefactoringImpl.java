/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.TurnRefsToSuperRefactoring;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;

/**
 * @author dsl
 */
public class TurnRefsToSuperRefactoringImpl extends RefactoringImpl<TurnRefsToSuperProcessor> implements TurnRefsToSuperRefactoring {
  TurnRefsToSuperRefactoringImpl(Project project, PsiClass aClass, PsiClass aSuper, boolean replaceInstanceOf) {
    super(new TurnRefsToSuperProcessor(project, aClass, aSuper, replaceInstanceOf));
  }

  public PsiClass getSuper() {
    return myProcessor.getSuper();
  }

  public PsiClass getTarget() {
    return myProcessor.getTarget();
  }

  public boolean isReplaceInstanceOf() {
    return myProcessor.isReplaceInstanceOf();
  }
}
