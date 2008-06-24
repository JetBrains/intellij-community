/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.rename.naming.AutomaticInheritorRenamerFactory;
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamerFactory;

/**
 * @author dsl
 */
public class JavaRenameRefactoringImpl extends RenameRefactoringImpl implements RenameRefactoring {
  private static final AutomaticVariableRenamerFactory ourVariableRenamerFactory = new AutomaticVariableRenamerFactory();
  private static final AutomaticInheritorRenamerFactory ourInheritorRenamerFactory = new AutomaticInheritorRenamerFactory();

  public JavaRenameRefactoringImpl(Project project,
                               PsiElement element,
                               String newName,
                               boolean toSearchInComments,
                               boolean toSearchInNonJavaFiles) {
    super(project, element, newName, toSearchInComments, toSearchInNonJavaFiles);
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
}
