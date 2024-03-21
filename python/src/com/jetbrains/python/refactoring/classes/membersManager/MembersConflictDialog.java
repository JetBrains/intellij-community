// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Displays error messages about fact that destination class already contains some member infos
 * or members under refactoring would not be available at the new place.
 *
 * @author Ilya.Kazakevich
 */
public class MembersConflictDialog extends ConflictsDialog {
  /**
   * @param project               project under refactoring
   * @param duplicatesConflict    duplicates conflicts : that means destination class has the same member.
   *                              If member "foo" already exists in class "bar": pass [bar] -] [foo].
   * @param dependenciesConflicts dependency conflict: list of elements used by member under refactoring and would not be available
   *                              at new destination. If user wants to move method, that uses field "bar" which would not be available at new class,
   *                              pass [bar] field
   */
  public MembersConflictDialog(
    final @NotNull Project project,
    final @NotNull MultiMap<PyClass, PyMemberInfo<?>> duplicatesConflict,
    final @NotNull Collection<PyMemberInfo<?>> dependenciesConflicts) {
    super(project, convertDescription(duplicatesConflict, dependenciesConflicts), null, true, false);
  }

  private static @NotNull MultiMap<PsiElement, String> convertDescription(
    final @NotNull MultiMap<PyClass, PyMemberInfo<?>> duplicateConflictDescriptions,
    final @NotNull Collection<PyMemberInfo<?>> dependenciesConflicts) {
    final MultiMap<PsiElement, String> result = new MultiMap<>();
    for (final PyClass aClass : duplicateConflictDescriptions.keySet()) {
      for (final PyMemberInfo<?> pyMemberInfo : duplicateConflictDescriptions.get(aClass)) {
        final String message = RefactoringBundle.message("0.already.contains.a.1",
                                                         RefactoringUIUtil.getDescription(aClass, false),
                                                         RefactoringUIUtil.getDescription(pyMemberInfo.getMember(), false));
        result.putValue(aClass, message);
      }
    }

    for (final PyMemberInfo<?> memberUnderConflict : dependenciesConflicts) {
      result.putValue(memberUnderConflict.getMember(), PyBundle.message(
                        "refactoring.will.not.be.accessible",
                        RefactoringUIUtil.getDescription(memberUnderConflict.getMember(), false)
                      )
      );
    }


    return result;
  }
}
