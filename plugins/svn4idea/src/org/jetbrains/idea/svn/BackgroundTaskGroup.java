// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList;

/**
 * Provides common cancellation and error handling behaviour for group of dependent tasks.
 *
 * @deprecated use {@link BackgroundTaskQueue}
 */
@Deprecated
public class BackgroundTaskGroup extends BackgroundTaskQueue {

  private static final Logger LOG = Logger.getInstance(BackgroundTaskGroup.class);

  @NotNull protected final List<VcsException> myExceptions = createLockFreeCopyOnWriteList();
  @NotNull private final Project myProject;

  public BackgroundTaskGroup(@NotNull Project project, @NotNull String title) {
    super(project, title);
    myProject = project;
  }

  @Override
  public void run(@NotNull Task.Backgroundable task, @Nullable ModalityState modalityState, @Nullable ProgressIndicator indicator) {
    throw new UnsupportedOperationException();
  }

  public void runInBackground(@NotNull String title, @NotNull ThrowableConsumer<ProgressIndicator, VcsException> task) {
    myProcessor.add(continuation -> new Task.Backgroundable(myProject, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          task.consume(indicator);
        }
        catch (VcsException e) {
          addError(e);
          if (!e.isWarning()) {
            indicator.cancel();
          }
        }
      }

      @Override
      public void onCancel() {
        end();
      }

      @Override
      public void onThrowable(@NotNull Throwable e) {
        LOG.error(e);
        end();
      }

      @Override
      public void onFinished() {
        continuation.run();
      }
    }.queue());
  }

  public void runInEdt(@NotNull ThrowableRunnable<VcsException> task) {
    myProcessor.add(continuation -> {
      boolean isSuccess = false;
      try {
        task.run();
        isSuccess = true;
      }
      catch (VcsException e) {
        addError(e);
        isSuccess = e.isWarning();
      }
      finally {
        if (!isSuccess) {
          end();
        }
        continuation.run();
      }
    });
  }

  public void addError(@NotNull VcsException e) {
    myExceptions.add(e);
  }

  public void showErrors() {
    if (!myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, myTitle);
    }
  }

  public void end() {
    myProcessor.clear();
    showErrors();
  }
}
