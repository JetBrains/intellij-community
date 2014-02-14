package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Ilya.Kazakevich
 */
public class PyPushDownViewSwingImpl extends MembersBasedViewSwingImpl<PyPushDownPresenter, MembersViewInitializationInfo>
  implements PyPushDownView {
  public PyPushDownViewSwingImpl(
    @NotNull final PyClass classUnderRefactoring,
    @NotNull final Project project,
    @NotNull final PyPushDownPresenter presenter) {
    super(project, presenter, RefactoringBundle.message("push.members.from.0.down.label", classUnderRefactoring.getName()), false);

    myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
    setTitle(PyPushDownHandler.REFACTORING_NAME);
  }
}
