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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

public class MergeTask extends BaseMergeTask {

  @NotNull private final Runnable myCallback;

  public MergeTask(@NotNull QuickMerge mergeProcess, @NotNull Runnable callback) {
    super(mergeProcess);
    myCallback = callback;
  }

  @Override
  public void run() {
    boolean needRefresh = setupDefaultEmptyChangeListForMerge();

    if (needRefresh) {
      ChangeListManager.getInstance(myMergeContext.getProject())
        .invokeAfterUpdate(myCallback, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE, "", ModalityState.NON_MODAL);
    }
    else {
      myCallback.run();
    }
  }

  private boolean setupDefaultEmptyChangeListForMerge() {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myMergeContext.getProject());
    int i = 0;
    boolean needRefresh = false;

    while (true) {
      String name = myMergeContext.getTitle() + (i > 0 ? " (" + i + ")" : "");
      LocalChangeList changeList = changeListManager.findChangeList(name);

      if (changeList == null) {
        changeListManager.setDefaultChangeList(changeListManager.addChangeList(name, null));
        needRefresh = true;
        break;
      }
      if (changeList.getChanges().isEmpty()) {
        if (!changeList.isDefault()) {
          changeListManager.setDefaultChangeList(changeList);
          needRefresh = true;
        }
        break;
      }
      i++;
    }

    return needRefresh;
  }
}
