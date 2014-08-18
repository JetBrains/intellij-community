package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager;

import static org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager.LOG;

class PullTask extends BaseRepositoryManager.Task {
  private final GitRepositoryManager manager;

  public PullTask(@NotNull GitRepositoryManager manager, @NotNull ProgressIndicator indicator) {
    super(indicator);

    this.manager = manager;
  }

  @Override
  protected void execute() throws Exception {
    JGitProgressMonitor progressMonitor = new JGitProgressMonitor(indicator);
    PullResult pullResult = manager.git.pull()
      .setProgressMonitor(progressMonitor)
      .setCredentialsProvider(manager.getCredentialsProvider())
      .setRebase(true)
      .call();
    if (LOG.isDebugEnabled()) {
      String messages = pullResult.getFetchResult().getMessages();
      if (!StringUtil.isEmptyOrSpaces(messages)) {
        LOG.debug(messages);
      }
    }

    MergeResult.MergeStatus mergeStatus = pullResult.getMergeResult().getMergeStatus();
    if (LOG.isDebugEnabled()) {
      LOG.debug(mergeStatus.toString());
    }
    if (!mergeStatus.isSuccessful()) {
      throw new UnsupportedOperationException();
    }
  }
}