package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.jetbrains.annotations.NotNull;

class JGitProgressMonitor implements ProgressMonitor {
  private final ProgressIndicator indicator;

  public JGitProgressMonitor(@NotNull ProgressIndicator indicator) {
    this.indicator = indicator;
  }

  @Override
  public void start(int totalTasks) {
  }

  @Override
  public void beginTask(String title, int totalWork) {
    indicator.setText2(title);
  }

  @Override
  public void update(int completed) {
    // todo
  }

  @Override
  public void endTask() {
    indicator.setText2("");
  }

  @Override
  public boolean isCancelled() {
    return indicator.isCanceled();
  }
}