// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private final Set<Change> mySelectedChanges = new HashSet<>();

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

  @NotNull
  public Collection<Change> getSelectedChanges() {
    return mySelectedChanges;
  }
}
