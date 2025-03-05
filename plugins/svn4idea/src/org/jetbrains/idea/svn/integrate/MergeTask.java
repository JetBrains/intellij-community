// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

public class MergeTask extends BaseMergeTask {

  private final @NotNull Runnable myCallback;

  public MergeTask(@NotNull QuickMerge mergeProcess, @NotNull Runnable callback) {
    super(mergeProcess);
    myCallback = callback;
  }

  @Override
  public void run() {
    boolean needRefresh = setupDefaultEmptyChangeListForMerge();

    if (needRefresh) {
      ChangeListManager.getInstance(myMergeContext.getProject()).invokeAfterUpdateWithProgress(false, null, myCallback);
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
      String name = myMergeContext.getMergeTitle() + (i > 0 ? " (" + i + ")" : "");
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
