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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class BaseMergeTask implements ThrowableRunnable<VcsException>, ThrowableConsumer<ProgressIndicator, VcsException> {

  @NotNull protected final QuickMerge myMergeProcess;
  @NotNull protected final MergeContext myMergeContext;
  @NotNull protected final QuickMergeInteraction myInteraction;

  public BaseMergeTask(@NotNull QuickMerge mergeProcess) {
    myMergeProcess = mergeProcess;
    myMergeContext = mergeProcess.getMergeContext();
    myInteraction = mergeProcess.getInteraction();
  }

  @Override
  public void consume(@NotNull ProgressIndicator indicator) throws VcsException {
    run();
  }

  @Override
  public void run() throws VcsException {
  }
}
