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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import com.intellij.openapi.vcs.changes.ThreadSafeTransparentlyFailedValue;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.Nullable;

public abstract class RunOrContinuation<T, E extends Exception> {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.dialogs.RunOrContinuation");
  protected final Project myProject;
  private final String myTaskTitle;
  private final Class<E> myClazzE;
  private volatile boolean myWasCanceled;
  private TransparentlyFailedValueI<T,E> myTransparentlyFailedValue;

  protected RunOrContinuation(final Project project, final String taskTitle, final Class<E> clazzE) {
    myProject = project;
    myTaskTitle = taskTitle;
    myClazzE = clazzE;
    myTransparentlyFailedValue = new ThreadSafeTransparentlyFailedValue<>();
  }

  @Nullable
  @CalledInAwt
  protected abstract T calculate() throws E;
  @Nullable
  @CalledInBackground
  protected abstract T calculateLong() throws E;
  @CalledInAwt
  protected abstract void processResult(final TransparentlyFailedValueI<T, E> t);

  protected void cancel() {
    myWasCanceled = true;
  }

  private void setException(Exception e) {
    if (myClazzE.isAssignableFrom(e.getClass())) {
      myTransparentlyFailedValue.fail((E)e);
    } else {
      LOG.info(e);
      myTransparentlyFailedValue.failRuntime((e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e)));
    }
  }

  @CalledInAwt
  public TaskDescriptor getTask() {
    final TaskDescriptor pooled = new TaskDescriptor(myTaskTitle, Where.POOLED) {
      @Override
      public void run(ContinuationContext context) {
        try {
          myTransparentlyFailedValue.set(calculateLong());
        }
        catch (Exception e) {
          setException(e);
        }
        if (! myWasCanceled) {
          context.next(new TaskDescriptor("final part", Where.AWT) {
            @Override
            public void run(ContinuationContext context) {
              processResult(myTransparentlyFailedValue);
            }
          });
        }
      }

    };

    return new TaskDescriptor("short part", Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        try {
          myTransparentlyFailedValue.set(calculate());
        }
        catch (Exception e) {
          setException(e);
        }
        if ((! myWasCanceled) && (myTransparentlyFailedValue.haveSomething())) {
          processResult(myTransparentlyFailedValue);
          return;
        }
        context.next(pooled);
      }
    };
  }

  @CalledInAwt
  public void execute() {
    Continuation.createFragmented(myProject, true).run(getTask());
  }
}
