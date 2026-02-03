// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownProcessor extends PyMembersRefactoringBaseProcessor {

  public PyPushDownProcessor(
    final @NotNull Project project,
    final @NotNull Collection<PyMemberInfo<PyElement>> membersToMove,
    final @NotNull PyClass from) {
    super(project, membersToMove, from, getChildren(from));
  }

  private static PyClass @NotNull [] getChildren(final @NotNull PyClass from) {
    final Collection<PyClass> all = getInheritors(from);
    return all.toArray(PyClass.EMPTY_ARRAY);
  }

  /**
   * @param from class to check for inheritors
   * @return inheritors of class
   */
  static @NotNull Collection<PyClass> getInheritors(final @NotNull PyClass from) {
    return PyClassInheritorsSearch.search(from, false).findAll();
  }


  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("push.down.members.elements.header", "");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Override
  protected @NotNull String getCommandName() {
    return PyPushDownHandler.getRefactoringName();
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.python.push.down";
  }
}
