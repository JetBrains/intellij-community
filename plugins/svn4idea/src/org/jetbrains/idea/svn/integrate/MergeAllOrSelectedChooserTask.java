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

import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;

public class MergeAllOrSelectedChooserTask extends BaseMergeTask {

  public MergeAllOrSelectedChooserTask(@NotNull QuickMerge mergeProcess) {
    super(mergeProcess, "merge source selector", Where.AWT);
  }

  @Override
  public void run() {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myInteraction.selectMergeVariant()) {
      case all:
        next(getMergeAllTasks(true));
        break;
      case showLatest:
        LoadRecentBranchRevisions loader = new LoadRecentBranchRevisions(myMergeProcess, -1);
        ShowRecentInDialogTask dialog = new ShowRecentInDialogTask(myMergeProcess, loader);

        next(loader, dialog);
        break;
      case select:
        next(new LookForBranchOriginTask(myMergeProcess, false, copyPoint ->
          next(new MergeCalculatorTask(myMergeProcess, copyPoint, task ->
            next(new ShowRevisionSelector(myMergeProcess, task.getChangeLists(), task.getMergeChecker()))))));
        break;
    }
  }
}
