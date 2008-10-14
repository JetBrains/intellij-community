package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FictiveBackgroundable extends Task.Backgroundable {
  private final Waiter myWaiter;

  FictiveBackgroundable(@Nullable final Project project, @NotNull final Runnable runnable, final boolean cancellable, final String title) {
    super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable, BackgroundFromStartOption.getInstance());
    myWaiter = new Waiter(project, runnable);
  }

  public void run(final ProgressIndicator indicator) {
    myWaiter.run();
  }

  public void done() {
    myWaiter.done();
  }
}
