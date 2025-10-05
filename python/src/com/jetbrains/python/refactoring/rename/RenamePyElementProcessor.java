// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;


public abstract class RenamePyElementProcessor extends RenamePsiElementProcessor {
  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element,
                                        @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts) {
    PyElement container = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
    if (container instanceof PyFile pyFile) {
      PyClass conflictingClass = pyFile.findTopLevelClass(newName);
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass, PyBundle.message("refactoring.rename.class.already.defined", newName, pyFile.getName()));
      }
      PyFunction conflictingFunction = pyFile.findTopLevelFunction(newName);
      if (conflictingFunction != null) {
        conflicts.putValue(conflictingFunction,
                           PyBundle.message("refactoring.rename.function.already.defined", newName, pyFile.getName()));
      }
      PyTargetExpression conflictingVariable = pyFile.findTopLevelAttribute(newName);
      if (conflictingVariable != null) {
        conflicts.putValue(conflictingVariable,
                           PyBundle.message("refactoring.rename.variable.already.defined", newName, pyFile.getName()));
      }
    }
    else if (container instanceof PyClass pyClass) {
      PyClass conflictingClass = pyClass.findNestedClass(newName, true);
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass,
                           PyBundle.message("refactoring.rename.nested.class.already.defined", newName, pyClass.getName()));
      }
      PyFunction conflictingFunction = pyClass.findMethodByName(newName, true, null);
      if (conflictingFunction != null) {
        conflicts.putValue(conflictingFunction,
                           PyBundle.message("refactoring.rename.method.already.defined", newName, pyClass.getName()));
      }
      PyTargetExpression conflictingAttribute = pyClass.findClassAttribute(newName, true, null);
      if (conflictingAttribute != null) {
        conflicts.putValue(conflictingAttribute,
                           PyBundle.message("refactoring.rename.class.attribute.already.defined", newName, pyClass.getName()));
      }
    }
  }

  @Override
  public String getHelpID(PsiElement element) {
    return "refactoring.renameDialogs";
  }
}
