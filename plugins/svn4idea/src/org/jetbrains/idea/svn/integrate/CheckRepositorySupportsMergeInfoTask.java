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

import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.dialogs.MergeContext;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CheckRepositorySupportsMergeInfoTask extends BaseMergeTask {

  public CheckRepositorySupportsMergeInfoTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    super(mergeContext, interaction, "Checking repository capabilities", Where.POOLED);
  }

  @Override
  public void run(ContinuationContext context) {
    final List<TaskDescriptor> tasks = new LinkedList<TaskDescriptor>();
    final boolean supportsMergeinfo = myMergeContext.getWcInfo().getFormat().supportsMergeInfo() && SvnUtil.checkRepositoryVersion15(
      myMergeContext.getVcs(), myMergeContext.getSourceUrl());
    if (!supportsMergeinfo) {
      insertMergeAll(tasks);
    }
    else {
      tasks.add(new MergeAllOrSelectedChooserTask(myMergeContext, myInteraction));
    }
    context.next(tasks);
  }
}
