package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * Displays error messages about fact that destination class already contains some member infos
 *
 * @author Ilya.Kazakevich
 */
public class AlreadyContainsMemberDialog extends ConflictsDialog {
  public AlreadyContainsMemberDialog(
    @NotNull final Project project,
    @NotNull final MultiMap<PyClass, PyMemberInfo<?>> conflictDescriptions) {
    super(project, convertDescription(conflictDescriptions), null, true, false);
  }

  @NotNull
  private static MultiMap<PsiElement, String> convertDescription(@NotNull final MultiMap<PyClass, PyMemberInfo<?>> descriptions) {
    final MultiMap<PsiElement, String> result = new MultiMap<PsiElement, String>();
    for (final PyClass aClass : descriptions.keySet()) {
      for (final PyMemberInfo<?> pyMemberInfo : descriptions.get(aClass)) {
        final String message = RefactoringBundle.message("0.already.contains.a.1",
                                                         RefactoringUIUtil.getDescription(aClass, false),
                                                         RefactoringUIUtil.getDescription(pyMemberInfo.getMember(), false));
        result.putValue(aClass, message);
      }
    }
    return result;
  }
}
