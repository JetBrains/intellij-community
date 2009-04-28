package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunnableBackgroundableWrapper extends Task.Backgroundable {
  private final Runnable myNonCancellable;

  public RunnableBackgroundableWrapper(@Nullable Project project, @NotNull String title, Runnable nonCancellable) {
    super(project, title, false, BackgroundFromStartOption.getInstance());
    myNonCancellable = nonCancellable;
  }

  public void run(@NotNull ProgressIndicator indicator) {
    myNonCancellable.run();
  }
}
