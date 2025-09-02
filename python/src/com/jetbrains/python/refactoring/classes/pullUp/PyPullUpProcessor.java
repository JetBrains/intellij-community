// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.pullUp;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public final class PyPullUpProcessor extends PyMembersRefactoringBaseProcessor {
  public PyPullUpProcessor(final @NotNull PyClass from, final @NotNull PyClass to, final @NotNull Collection<PyMemberInfo<PyElement>> membersToMove) {
    super(from.getProject(), membersToMove, from, to);
  }

  @Override
  protected @NotNull String getCommandName() {
    return PyPullUpHandler.getRefactoringName();
  }

  @Override
  public String getProcessedElementsHeader() {
    return PyBundle.message("refactoring.pull.up.dialog.move.members.to.class");
  }

  @Override
  public @NotNull String getCodeReferencesText(final int usagesCount, final int filesCount) {
    return PyBundle.message("refactoring.pull.up.dialog.members.to.be.moved");
  }

  @Override
  public @NotNull String getCommentReferencesText(final int usagesCount, final int filesCount) {
    return getCodeReferencesText(usagesCount, filesCount);
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "refactoring.python.pull.up";
  }
}
