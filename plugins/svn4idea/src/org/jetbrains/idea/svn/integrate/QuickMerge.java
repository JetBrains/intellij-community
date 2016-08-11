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
import org.jetbrains.annotations.CalledInAwt;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.TaskDescriptor;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class QuickMerge {

  @NotNull private final MergeContext myMergeContext;
  private final Continuation myContinuation;
  private QuickMergeInteraction myInteraction;

  public QuickMerge(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myContinuation = Continuation.createFragmented(mergeContext.getProject(), true);
  }

  @CalledInAwt
  public void execute(@NotNull final QuickMergeInteraction interaction, @NotNull final TaskDescriptor... finalTasks) {
    myInteraction = interaction;
    myInteraction.setTitle(myMergeContext.getTitle());

    FileDocumentManager.getInstance().saveAllDocuments();

    final List<TaskDescriptor> tasks = new LinkedList<>();
    tasks.add(new MergeInitChecksTask(myMergeContext, myInteraction));
    tasks.add(new CheckRepositorySupportsMergeInfoTask(myMergeContext, myInteraction));
    if (finalTasks.length > 0) {
      tasks.addAll(Arrays.asList(finalTasks));
    }

    myContinuation.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
      @Override
      public void consume(VcsException e) {
        myInteraction.showErrors(myMergeContext.getTitle(), Collections.singletonList(e));
      }
    });
    myContinuation.addExceptionHandler(SVNException.class, new Consumer<SVNException>() {
      @Override
      public void consume(SVNException e) {
        myInteraction.showErrors(myMergeContext.getTitle(), Collections.singletonList(new VcsException(e)));
      }
    });
    myContinuation.addExceptionHandler(RuntimeException.class, new Consumer<RuntimeException>() {
      @Override
      public void consume(RuntimeException e) {
        myInteraction.showError(e);
      }
    });
    myContinuation.run(tasks);
  }
}
