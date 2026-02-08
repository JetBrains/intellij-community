// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;

/**
 * @author Ilya.Kazakevich
 */
public class PyPushDownViewSwingImpl extends MembersBasedViewSwingImpl<PyPushDownPresenter, MembersViewInitializationInfo>
  implements PyPushDownView {
  public PyPushDownViewSwingImpl(
    final @NotNull PyClass classUnderRefactoring,
    final @NotNull Project project,
    final @NotNull PyPushDownPresenter presenter) {
    super(project, presenter, RefactoringBundle.message("push.members.from.0.down.label", classUnderRefactoring.getName()), false);

    myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
    setTitle(PyPushDownHandler.getRefactoringName());
  }

  @Override
  protected @Nullable String getHelpId() {
    return "refactoring.pushMembersDown";
  }
}
