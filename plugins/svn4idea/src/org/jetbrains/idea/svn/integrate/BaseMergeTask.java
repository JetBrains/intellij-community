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
import org.jetbrains.annotations.CalledInAny;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

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

  @NotNull
  protected List<TaskDescriptor> getMergeAllTasks() {
    List<TaskDescriptor> result = ContainerUtil.newArrayList();

    result.add(new LocalChangesPromptTask(myMergeContext, myInteraction, true, null, null));
    MergeAllWithBranchCopyPointTask mergeAllExecutor = new MergeAllWithBranchCopyPointTask(myMergeContext, myInteraction);
    result.add(myMergeContext.getVcs().getSvnBranchPointsCalculator()
                 .getFirstCopyPointTask(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl(),
                                        myMergeContext.getWcInfo().getRootUrl(), mergeAllExecutor));
    result.add(mergeAllExecutor);

    return result;
  }

  protected void runChangeListsMerge(@NotNull ContinuationContext context,
                                     @NotNull final List<CommittedChangeList> lists,
                                     @NotNull SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                                     @NotNull String title) {
    context.next(new LocalChangesPromptTask(myMergeContext, myInteraction, false, lists, copyPoint),
                 new MergeTask(myMergeContext, myInteraction, new ChangeListsMergerFactory(lists, false, false, true), title));
  }

  @Nullable
  protected SVNURL parseSourceUrl(@NotNull ContinuationContext context) {
    SVNURL result = null;

    try {
      result = SvnUtil.createUrl(myMergeContext.getSourceUrl());
    }
    catch (SvnBindException e) {
      finishWithError(context, e.getMessage(), true);
    }

    return result;
  }

  @CalledInAny
  protected void finishWithError(@NotNull ContinuationContext context, @NotNull final String message, final boolean isError) {
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
  protected void finishWithError(@NotNull ContinuationContext context,
                                 final String message,
                                 @Nullable final List<VcsException> exceptions) {
    log(message, exceptions);

    context.cancelEverything();
    context.next(new TaskDescriptor(message, Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        myInteraction.showErrors(message, exceptions);
      }
    });
  }

  private static void log(String message, @Nullable List<VcsException> exceptions) {
    if (exceptions != null) {
      for (VcsException exception : exceptions) {
        LOG.info(message, exception);
      }
    }
  }
}
