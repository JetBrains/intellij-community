package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager;

import java.io.IOException;
import java.util.Iterator;

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
    FetchResult fetchResult = manager.git.fetch().setRemoveDeletedRefs(true).setProgressMonitor(progressMonitor).setCredentialsProvider(manager.getCredentialsProvider()).call();
    if (LOG.isDebugEnabled()) {
      String messages = fetchResult.getMessages();
      if (!StringUtil.isEmptyOrSpaces(messages)) {
        LOG.debug(messages);
      }
    }

    Iterator<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates().iterator();
    TrackingRefUpdate refUpdate = refUpdates.hasNext() ? refUpdates.next() : null;
    if (refUpdate == null || refUpdate.getResult() == RefUpdate.Result.NO_CHANGE || refUpdate.getResult() == RefUpdate.Result.FORCED) {
      LOG.debug("Nothing to merge");
      return;
    }

    int attemptCount = 0;
    do {
      MergeCommand mergeCommand = manager.git.merge();
      Ref ref = getUpstreamBranchRef();
      if (ref == null) {
        throw new AssertionError();
      }
      else {
        mergeCommand.include(ref);
      }

     MergeResult mergeResult = mergeCommand.setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call();
      if (LOG.isDebugEnabled()) {
        LOG.debug(mergeResult.toString());
      }

      MergeResult.MergeStatus status = mergeResult.getMergeStatus();
      if (status.isSuccessful()) {
        rebase(progressMonitor);
        return;
      }
      else if (status != MergeResult.MergeStatus.ABORTED) {
        break;
      }
    }
    while (++attemptCount < 3);
  }

  private Ref getUpstreamBranchRef() throws IOException {
    return manager.git.getRepository().getRef(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
  }

  private void rebase(@NotNull JGitProgressMonitor progressMonitor) throws GitAPIException {
    RebaseResult result = null;
    do {
      if (result == null) {
        result = manager.git.rebase().setUpstream(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER).setProgressMonitor(progressMonitor).call();
      }
      else if (result.getStatus() == RebaseResult.Status.CONFLICTS) {
        throw new UnsupportedOperationException();
      }
      else if (result.getStatus() == RebaseResult.Status.NOTHING_TO_COMMIT) {
        result = manager.git.rebase().setOperation(RebaseCommand.Operation.SKIP).call();
      }
      else {
        throw new UnsupportedOperationException();
      }
    }
    while (!result.getStatus().isSuccessful());
  }
}