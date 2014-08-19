package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import static org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager.LOG;

class PullTask {
  public static void execute(@NotNull GitRepositoryManager manager, @NotNull ProgressIndicator indicator) throws Exception {
    LOG.debug("Pull");

    FetchResult fetchResult = manager.git.fetch()
      .setRemoveDeletedRefs(true)
      .setProgressMonitor(new JGitProgressMonitor(indicator))
      .setCredentialsProvider(manager.getCredentialsProvider())
      .call();

    GitRepositoryManager.printMessages(fetchResult);

    Iterator<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates().iterator();
    TrackingRefUpdate refUpdate = refUpdates.hasNext() ? refUpdates.next() : null;
    if (refUpdate == null || refUpdate.getResult() == RefUpdate.Result.NO_CHANGE || refUpdate.getResult() == RefUpdate.Result.FORCED) {
      LOG.debug("Nothing to merge");
      return;
    }

    Ref refToMerge = fetchResult.getAdvertisedRef(Constants.MASTER);
    if (refToMerge == null) {
      refToMerge = fetchResult.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER);
    }
    if (refToMerge == null) {
      throw new JGitInternalException("Could not get advertised ref");
    }

    MergeResult mergeResult = manager.git.merge().include(refToMerge).call();
    MergeResult.MergeStatus mergeStatus = mergeResult.getMergeStatus();
    if (LOG.isDebugEnabled()) {
      LOG.debug(mergeStatus.toString());
    }
    if (!mergeStatus.isSuccessful()) {
      throw new UnsupportedOperationException();
    }
  }
}