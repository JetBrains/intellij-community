package com.jetbrains.python.refactoring.classes.pullUp;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 *
 *
 * @author Ilya.Kazakevich
 */
class PyPullUpProcessor extends PyMembersRefactoringBaseProcessor {

  PyPullUpProcessor(@NotNull final PyClass from, @NotNull final PyClass to, @NotNull final Collection<PyMemberInfo<PyElement>> membersToMove) {
    super(from.getProject(), membersToMove, from, to);
  }


  @Override
  protected String getCommandName() {
    return PyPullUpHandler.REFACTORING_NAME;
  }

  @Override
  public String getProcessedElementsHeader() {
    return PyBundle.message("refactoring.pull.up.dialog.move.members.to.class");
  }

  @Override
  public String getCodeReferencesText(final int usagesCount, final int filesCount) {
    return PyBundle.message("refactoring.pull.up.dialog.members.to.be.moved");
  }

  @Nullable
  @Override
  public String getCommentReferencesText(final int usagesCount, final int filesCount) {
    return getCodeReferencesText(usagesCount, filesCount);
  }
}
