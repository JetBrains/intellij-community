// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    @NotNull final Project project,
    @NotNull final Collection<PyMemberInfo<PyElement>> membersToMove,
    @NotNull final PyClass from) {
    super(project, membersToMove, from, getChildren(from));
  }

  private static PyClass @NotNull [] getChildren(@NotNull final PyClass from) {
    final Collection<PyClass> all = getInheritors(from);
    return all.toArray(PyClass.EMPTY_ARRAY);
  }

  /**
   * @param from class to check for inheritors
   * @return inheritors of class
   */
  @NotNull
  static Collection<PyClass> getInheritors(@NotNull final PyClass from) {
    return PyClassInheritorsSearch.search(from, false).findAll();
  }


  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("push.down.members.elements.header", "");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return PyPushDownHandler.getRefactoringName();
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.python.push.down";
  }
}
