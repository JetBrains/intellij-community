// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SelectedChangeSetChecker extends SelectedChangeListsChecker {
  private final @NotNull Set<Change> mySelectedChanges = new HashSet<>();

  private void fillChanges(final AnActionEvent event) {
    mySelectedChanges.clear();

    final Change[] changes = event.getData(VcsDataKeys.CHANGES);
    if (changes != null) {
      ContainerUtil.addAll(mySelectedChanges, changes);
    }
  }

  @Override
  public void execute(final AnActionEvent event) {
    super.execute(event);
    fillChanges(event);
  }

  @Override
  public boolean isValid() {
    return super.isValid() && (myChangeListsList.size() == 1) && (!mySelectedChanges.isEmpty());
  }

  public @NotNull Collection<Change> getSelectedChanges() {
    return mySelectedChanges;
  }
}
