/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.continuation.SeparatePiecesRunner;
import com.intellij.util.continuation.TaskDescriptor;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.tmatesoft.svn.core.SVNException;

import static com.intellij.util.Functions.identity;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static java.util.Collections.singletonList;

public class QuickMerge {

  @NotNull private final MergeContext myMergeContext;
  @NotNull private final QuickMergeInteraction myInteraction;
  @NotNull private final SeparatePiecesRunner myRunner;

  public QuickMerge(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) {
    myMergeContext = mergeContext;
    myInteraction = interaction;
    myRunner = createRunner();
  }

  @NotNull
  public MergeContext getMergeContext() {
    return myMergeContext;
  }

  @NotNull
  public QuickMergeInteraction getInteraction() {
    return myInteraction;
  }

  @NotNull
  public SeparatePiecesRunner getRunner() {
    return myRunner;
  }

  @CalledInAwt
  public void execute() {
    runMergeTasks(null);
  }

  @TestOnly
  @CalledInAwt
  public void execute(@NotNull TaskDescriptor finalTask) {
    runMergeTasks(finalTask);
  }

  @CalledInAwt
  private void runMergeTasks(@Nullable TaskDescriptor finalTask) {
    FileDocumentManager.getInstance().saveAllDocuments();

    TaskDescriptor[] tasks = {
      new MergeInitChecksTask(this),
      new CheckRepositorySupportsMergeInfoTask(this),
      finalTask
    };

    myRunner.next(mapNotNull(tasks, identity()));
    myRunner.ping();
  }

  @NotNull
  private SeparatePiecesRunner createRunner() {
    SeparatePiecesRunner result = new SeparatePiecesRunner(myMergeContext.getProject(), true);

    result.addExceptionHandler(VcsException.class, e -> myInteraction.showErrors(myMergeContext.getTitle(), singletonList(e)));
    result.addExceptionHandler(SVNException.class,
                               e -> myInteraction.showErrors(myMergeContext.getTitle(), singletonList(new VcsException(e))));
    result.addExceptionHandler(RuntimeException.class, e -> myInteraction
      .showErrors(notNull(e.getMessage(), e.getClass().getName()), singletonList(new VcsException(e))));

    return result;
  }
}
