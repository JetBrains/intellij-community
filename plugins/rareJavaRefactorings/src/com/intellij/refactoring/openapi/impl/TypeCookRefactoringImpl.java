// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.TypeCookRefactoring;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.TypeCookProcessor;

import java.util.List;

public class TypeCookRefactoringImpl extends RefactoringImpl<TypeCookProcessor> implements TypeCookRefactoring {
  TypeCookRefactoringImpl(Project project,
                          PsiElement[] elements,
                          final boolean dropObsoleteCasts,
                          final boolean leaveObjectsRaw,
                          final boolean preserveRawArrays,
                          final boolean exhaustiveSearch,
                          final boolean cookObjects,
                          final boolean cookToWildcards) {
    super(new TypeCookProcessor(project, elements, new Settings() {
      @Override
      public boolean dropObsoleteCasts() {
        return dropObsoleteCasts;
      }

      @Override
      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectsRaw;
      }

      @Override
      public boolean exhaustive() {
        return exhaustiveSearch;
      }

      @Override
      public boolean cookObjects() {
        return cookObjects;
      }

      @Override
      public boolean cookToWildcards() {
        return cookToWildcards;
      }

      @Override
      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }
    }));
  }

  @Override
  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }
}
