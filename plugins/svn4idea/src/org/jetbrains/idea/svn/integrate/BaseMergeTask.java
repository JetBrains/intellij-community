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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.SeparatePiecesRunner;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static java.util.Collections.singletonList;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.jetbrains.idea.svn.WorkingCopyFormat.ONE_DOT_EIGHT;

public abstract class BaseMergeTask extends TaskDescriptor {

  private static final Logger LOG = Logger.getInstance(BaseMergeTask.class);

  @NotNull protected final QuickMerge myMergeProcess;
  @NotNull protected final MergeContext myMergeContext;
  @NotNull protected final QuickMergeInteraction myInteraction;
  @NotNull private final SeparatePiecesRunner myRunner;

  public BaseMergeTask(@NotNull QuickMerge mergeProcess, @NotNull String name, @NotNull Where where) {
    super(name, where);

    myMergeProcess = mergeProcess;
    myMergeContext = mergeProcess.getMergeContext();
    myInteraction = mergeProcess.getInteraction();
    myRunner = mergeProcess.getRunner();
  }

  protected boolean is18() {
    return myMergeContext.getWcInfo().getFormat().isOrGreater(ONE_DOT_EIGHT);
  }

  @Override
  public void run(@NotNull ContinuationContext context) {
    try {
      run();
    }
    catch (VcsException e) {
      end(e);
    }
  }

  public void run() throws VcsException {
  }

  @CalledInAny
  protected void next(@NotNull TaskDescriptor... tasks) {
    myRunner.next(tasks);
  }

  protected void mergeAll(boolean supportsMergeInfo) {
    // merge info is not supported - branch copy point is used to make first sync merge successful (without unnecessary tree conflicts)
    // merge info is supported and svn client < 1.8 - branch copy point is used to determine if sync or reintegrate merge should be performed
    // merge info is supported and svn client >= 1.8 - branch copy point is not used - svn automatically detects if reintegrate is necessary
    next(supportsMergeInfo && is18()
         ? new MergeAllWithBranchCopyPointTask(myMergeProcess)
         : new LookForBranchOriginTask(myMergeProcess, true, copyPoint ->
           next(new MergeAllWithBranchCopyPointTask(myMergeProcess, copyPoint, supportsMergeInfo))));
  }

  protected void merge(@NotNull List<SvnChangeList> changeLists) {
    if (!changeLists.isEmpty()) {
      ChangeListsMergerFactory mergerFactory = new ChangeListsMergerFactory(changeLists, false, false, true);

      merge(myMergeContext.getTitle(), mergerFactory, changeLists);
    }
  }

  protected void merge(@NotNull String title, @NotNull MergerFactory mergerFactory, @Nullable List<SvnChangeList> changeLists) {
    next(new LocalChangesPromptTask(myMergeProcess, changeLists, () ->
      next(new MergeTask(myMergeProcess, () ->
        newIntegrateTask(title, mergerFactory).queue()))));
  }

  @NotNull
  protected Task newIntegrateTask(@NotNull String title, @NotNull MergerFactory mergerFactory) {
    return new SvnIntegrateChangesTask(myMergeContext.getVcs(), new WorkingCopyInfo(myMergeContext.getWcInfo().getPath(), true),
                                       mergerFactory, parseUrl(myMergeContext.getSourceUrl()), title, false,
                                       myMergeContext.getBranchName());
  }

  protected void end() {
    myRunner.cancelEverything();
  }

  @CalledInAny
  protected void end(@NotNull String message, boolean isError) {
    LOG.info((isError ? "Error: " : "Info: ") + message);

    end();
    getApplication().invokeLater(() -> myInteraction.showErrors(message, isError));
  }

  @CalledInAny
  protected void end(@NotNull VcsException e) {
    end(myMergeContext.getTitle(), e);
  }

  @CalledInAny
  protected void end(@NotNull String message, @NotNull VcsException e) {
    LOG.info(message, e);

    end();
    getApplication().invokeLater(() -> myInteraction.showErrors(message, singletonList(e)));
  }
}
