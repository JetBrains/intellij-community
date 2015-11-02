/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public abstract class RenamePyElementProcessor extends RenamePsiElementProcessor {
  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    PyElement container = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
    if (container instanceof PyFile) {
      PyFile pyFile = (PyFile)container;
      PyClass conflictingClass = pyFile.findTopLevelClass(newName);
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass, "A class named '" + newName + "' is already defined in " + pyFile.getName());
      }
      PyFunction conflictingFunction = pyFile.findTopLevelFunction(newName);
      if (conflictingFunction != null) {
        conflicts.putValue(conflictingFunction, "A function named '" + newName + "' is already defined in " + pyFile.getName());
      }
      PyTargetExpression conflictingVariable = pyFile.findTopLevelAttribute(newName);
      if (conflictingVariable != null) {
        conflicts.putValue(conflictingFunction, "A variable named '" + newName + "' is already defined in " + pyFile.getName());
      }
    }
    else if (container instanceof PyClass) {
      PyClass pyClass = (PyClass)container;
      PyClass conflictingClass = pyClass.findNestedClass(newName, true);
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass, "A class named '" + newName + "' is already defined in class '" + pyClass.getName() + "'");
      }
      PyFunction conflictingFunction = pyClass.findMethodByName(newName, true, null);
      if (conflictingFunction != null) {
        conflicts.putValue(conflictingFunction, "A function named '" + newName + "' is already defined in class '" + pyClass.getName() + "'");
      }
      PyTargetExpression conflictingAttribute = pyClass.findClassAttribute(newName, true, null);
      if (conflictingAttribute != null) {
        conflicts.putValue(conflictingAttribute, "An attribute named '" + newName + "' is already defined in class '" + pyClass.getName() + "'");
      }
    }
  }

  @Override
  public String getHelpID(PsiElement element) {
    return "python.reference.rename";
  }
}
