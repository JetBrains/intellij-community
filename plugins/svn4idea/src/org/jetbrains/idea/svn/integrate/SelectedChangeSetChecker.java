/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public void execute(final AnActionEvent event) {
    super.execute(event);
    fillChanges(event);
  }

  public boolean isValid() {
    return super.isValid() && (myChangeListsList.size() == 1) && (!mySelectedChanges.isEmpty());
  }

  public List<Change> getSelectedChanges() {
    return mySelectedChanges;
  }
}
