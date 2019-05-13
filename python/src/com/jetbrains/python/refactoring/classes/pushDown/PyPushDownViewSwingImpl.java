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
package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  protected String getHelpId() {
    return "refactoring.pushMembersDown";
  }
}
