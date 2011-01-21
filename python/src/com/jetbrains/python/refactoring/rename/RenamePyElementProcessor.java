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
      PyFunction conflictingFunction = pyClass.findMethodByName(newName, true);
      if (conflictingFunction != null) {
        conflicts.putValue(conflictingFunction, "A function named '" + newName + "' is already defined in class '" + pyClass.getName() + "'");
      }
      PyTargetExpression conflictingAttribute = pyClass.findClassAttribute(newName, true);
      if (conflictingAttribute != null) {
        conflicts.putValue(conflictingAttribute, "An attribute named '" + newName + "' is already defined in class '" + pyClass.getName() + "'");
      }
    }
  }
}
