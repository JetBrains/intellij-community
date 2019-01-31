// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;

import java.util.ArrayList;
import java.util.List;

public class SelectedChangeSetChecker extends SelectedChangeListsChecker {
  private final List<Change> mySelectedChanges;

  public SelectedChangeSetChecker() {
    super();
    mySelectedChanges = new ArrayList<>();
  }

  private void fillChanges(final AnActionEvent event) {
    mySelectedChanges.clear();

    final Change[] changes = event.getData(VcsDataKeys.CHANGES);
    if (changes != null) {
      // bugfix: event.getData(VcsDataKeys.CHANGES) return list with duplicates.
      // so the check is added here
      for (Change change : changes) {
        if (!mySelectedChanges.contains(change)) {
          mySelectedChanges.add(change);
        }
      }
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

  public List<Change> getSelectedChanges() {
    return mySelectedChanges;
  }
}
