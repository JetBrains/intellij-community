/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.Nullable;

public abstract class RunOrContinuation<T> {
  protected final Project myProject;
  private final String myTaskTitle;
  private boolean myWasCanceled;

  protected RunOrContinuation(final Project project, final String taskTitle) {
    myProject = project;
    myTaskTitle = taskTitle;
  }

  @Nullable
  @CalledInAwt
  protected abstract T calculate();
  @Nullable
  @CalledInBackground
  protected abstract T calculateLong();
  @CalledInAwt
  protected abstract void processResult(final T t);

  protected void cancel() {
    myWasCanceled = true;
  }

  @CalledInAwt
  public TaskDescriptor getTask() {
    final Ref<T> refT = new Ref<T>();

    final TaskDescriptor pooled = new TaskDescriptor(myTaskTitle, Where.POOLED) {
      @Override
      public void run(ContinuationContext context) {
        refT.set(calculateLong());
        if (! myWasCanceled) {
          context.next(new TaskDescriptor("final part", Where.AWT) {
            @Override
            public void run(ContinuationContext context) {
              processResult(refT.get());
            }
          });
        }
      }
    };

    return new TaskDescriptor("short part", Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        refT.set(calculate());
        if ((! myWasCanceled) && (! refT.isNull())) {
          processResult(refT.get());
          return;
        }
        context.next(pooled);
      }
    };
  }

  @CalledInAwt
  public void execute() {
    new Continuation(myProject, true).run(getTask());
  }

  /*@CalledInAwt
  public void execute() {
    final Ref<T> refT = new Ref<T>();
    refT.set(calculate());
    if ((! myWasCanceled) && (! refT.isNull())) {
      processResult(refT.get());
      return;
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myTaskTitle, true, BackgroundFromStartOption.getInstance()) {
      public void run(@NotNull ProgressIndicator indicator) {
        refT.set(calculateLong());
      }
      @Override
      public void onSuccess() {
        if (! myWasCanceled) {
          processResult(refT.get());
        }
      }
    });
  } */
}
