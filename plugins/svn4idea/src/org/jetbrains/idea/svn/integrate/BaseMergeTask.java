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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.CalledInAny;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.MergeContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseMergeTask extends TaskDescriptor {

  private static final Logger LOG = Logger.getInstance(BaseMergeTask.class);

  @NotNull protected final MergeContext myMergeContext;
  @NotNull protected final QuickMergeInteraction myInteraction;

  public BaseMergeTask(@NotNull MergeContext mergeContext,
                       @NotNull QuickMergeInteraction interaction, String name,
                       @NotNull Where where) {
    super(name, where);
    myMergeContext = mergeContext;
    myInteraction = interaction;
  }

  protected void insertMergeAll(final ContinuationContext context) {
    final List<TaskDescriptor> queue = new ArrayList<TaskDescriptor>();
    insertMergeAll(queue);
    context.next(queue);
  }

  protected void insertMergeAll(final List<TaskDescriptor> queue) {
    queue.add(new LocalChangesPromptTask(myMergeContext, myInteraction, true, null, null));
    final MergeAllWithBranchCopyPointTask mergeAllExecutor = new MergeAllWithBranchCopyPointTask(myMergeContext, myInteraction);
    queue.add(myMergeContext.getVcs().getSvnBranchPointsCalculator()
                .getFirstCopyPointTask(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl(),
                                       myMergeContext.getWcInfo().getRootUrl(), mergeAllExecutor));
    queue.add(mergeAllExecutor);
  }

  @CalledInAny
  protected void finishWithError(final ContinuationContext context, final String message, final boolean isError) {
    LOG.info((isError ? "Error: " : "Info: ") + message);
    context.next(new TaskDescriptor(message, Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        myInteraction.showErrors(message, isError);
        context.cancelEverything();
      }
    });
  }

  @CalledInAny
  protected void finishWithError(final ContinuationContext context, final String message, final List<VcsException> exceptions) {
    if (exceptions != null) {
      for (VcsException exception : exceptions) {
        LOG.info(message, exception);
      }
    }
    context.cancelEverything();
    context.next(new TaskDescriptor(message, Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        myInteraction.showErrors(message, exceptions);
      }
    });
  }
}
