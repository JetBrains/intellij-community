/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.MoveMembersRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class MoveMembersRefactoringImpl extends RefactoringImpl<MoveMembersProcessor> implements MoveMembersRefactoring {
  MoveMembersRefactoringImpl(Project project, final PsiMember[] elements, final String targetClassQualifiedName, final String newVisibility, final boolean makeEnumConstants) {
    super(new MoveMembersProcessor(project, new MoveMembersOptions() {
      public PsiMember[] getSelectedMembers() {
        return elements;
      }

      public String getTargetClassName() {
        return targetClassQualifiedName;
      }

      public String getMemberVisibility() {
        return newVisibility;
      }

      public boolean makeEnumConstant() {
        return makeEnumConstants;
      }

    }));
  }

  public List<PsiElement> getMembers() {
    return myProcessor.getMembers();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }
}
