// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class BaseMergeTask implements ThrowableRunnable<VcsException>, ThrowableConsumer<ProgressIndicator, VcsException> {

  protected final @NotNull QuickMerge myMergeProcess;
  protected final @NotNull MergeContext myMergeContext;
  protected final @NotNull QuickMergeInteraction myInteraction;

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
