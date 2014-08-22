package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import static org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager.LOG;

class PullTask {
  public static void execute(@NotNull GitRepositoryManager manager, @NotNull ProgressIndicator indicator) throws Exception {
    LOG.debug("Pull");

    MergeResult mergeResult = fetch(manager, indicator, null);
    if (mergeResult == null) {
      return;
    }

    MergeResult.MergeStatus mergeStatus = mergeResult.getMergeStatus();
    if (LOG.isDebugEnabled()) {
      LOG.debug(mergeStatus.toString());
    }
    if (!mergeStatus.isSuccessful()) {
      throw new UnsupportedOperationException();
    }
  }

  @Nullable
  private static MergeResult fetch(@NotNull GitRepositoryManager manager, @NotNull ProgressIndicator indicator, @Nullable RefUpdate.Result prevRefUpdateResult) throws Exception {
    // we must use the same StoredConfig instance during the operation
    StoredConfig config = manager.git.getRepository().getConfig();
    RemoteConfig remoteConfig = new RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME);
    Transport transport = Transport.open(manager.git.getRepository(), remoteConfig);
    FetchResult fetchResult;
    try {
      transport.setCredentialsProvider(manager.getCredentialsProvider());
      fetchResult = transport.fetch(new JGitProgressMonitor(indicator), null);
    }
    finally {
      transport.close();
    }

    GitRepositoryManager.printMessages(fetchResult);

    Collection<TrackingRefUpdate> trackingRefUpdates = fetchResult.getTrackingRefUpdates();
    if (trackingRefUpdates.isEmpty()) {
      LOG.debug("No remote changes (ref updates is empty)");
    }

    if (LOG.isDebugEnabled()) {
      for (TrackingRefUpdate refUpdate : trackingRefUpdates) {
        LOG.debug(refUpdate.toString());
      }
    }

    List<RefSpec> fetchRefSpecs = remoteConfig.getFetchRefSpecs();

    boolean hasChanges = false;
    for (RefSpec fetchRefSpec : fetchRefSpecs) {
      TrackingRefUpdate refUpdate = fetchResult.getTrackingRefUpdate(fetchRefSpec.getDestination());
      if (refUpdate == null) {
        LOG.debug("No ref update for " + fetchRefSpec);
        continue;
      }

      RefUpdate.Result refUpdateResult = refUpdate.getResult();

      // we can have more than one fetch ref spec, but currently we don't worry about it
      if (refUpdateResult == RefUpdate.Result.LOCK_FAILURE || refUpdateResult == RefUpdate.Result.IO_FAILURE) {
        if (prevRefUpdateResult == refUpdateResult) {
          throw new IOException("Ref update result " + refUpdateResult.name() + ", we have already tried to fetch again, but no luck");
        }

        LOG.warn("Ref update result " + refUpdateResult.name() + ", trying again after 500 ms");
        //noinspection BusyWait
        Thread.sleep(500);
        return fetch(manager, indicator, refUpdateResult);
      }

      if (!(refUpdateResult == RefUpdate.Result.FAST_FORWARD || refUpdateResult == RefUpdate.Result.NEW || refUpdateResult == RefUpdate.Result.FORCED)) {
        throw new UnsupportedOperationException("Unsupported ref update result");
      }

      if (!hasChanges) {
        hasChanges = refUpdateResult != RefUpdate.Result.NO_CHANGE;
      }
    }

    if (!hasChanges) {
      LOG.debug("No remote changes");
      return null;
    }

    String remoteBranchFullName = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE);
    if (StringUtil.isEmpty(remoteBranchFullName)) {
      throw new IllegalStateException("branch.master.merge refspec must be specified");
    }

    Ref refToMerge = fetchResult.getAdvertisedRef(remoteBranchFullName);
    if (refToMerge == null) {
      throw new IllegalStateException("Could not get advertised ref");
    }
    return merge(config, manager.git, refToMerge);
  }

  @NotNull
  public static MergeResult merge(@NotNull StoredConfig config, @NotNull Git git, @NotNull Ref ref) throws Exception {
    Repository repository = git.getRepository();

    Ref head = repository.getRef(Constants.HEAD);
    if (head == null) {
      throw new NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
    }

    MergeConfig mergeConfig = config.get(MergeConfig.getParser(Constants.MASTER));
    boolean squash = mergeConfig.isSquash();
    boolean commit = mergeConfig.isCommit();
    FastForwardMode fastForwardMode = mergeConfig.getFastForwardMode();

    if (squash && fastForwardMode == FastForwardMode.NO_FF) {
      throw new JGitInternalException(JGitText.get().cannotCombineSquashWithNoff);
    }

    MergeStrategy mergeStrategy = MergeStrategy.RECURSIVE;

    RevWalk revWalk = null;
    DirCacheCheckout dirCacheCheckout = null;
    try {
      // Check for FAST_FORWARD, ALREADY_UP_TO_DATE
      revWalk = new RevWalk(repository);

      // handle annotated tags
      ref = repository.peel(ref);
      ObjectId objectId = ref.getPeeledObjectId();
      if (objectId == null) {
        objectId = ref.getObjectId();
      }

      RevCommit srcCommit = revWalk.lookupCommit(objectId);
      ObjectId headId = head.getObjectId();
      if (headId == null) {
        revWalk.parseHeaders(srcCommit);
        dirCacheCheckout = new DirCacheCheckout(repository, repository.lockDirCache(), srcCommit.getTree());
        dirCacheCheckout.setFailOnConflict(true);
        dirCacheCheckout.checkout();
        RefUpdate refUpdate = repository.updateRef(head.getTarget().getName());
        refUpdate.setNewObjectId(objectId);
        refUpdate.setExpectedOldObjectId(null);
        refUpdate.setRefLogMessage("initial pull", false);
        if (refUpdate.update() != RefUpdate.Result.NEW) {
          throw new NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
        }
        return new MergeResult(srcCommit, srcCommit, new ObjectId[]{null, srcCommit}, MergeResult.MergeStatus.FAST_FORWARD, mergeStrategy, null);
      }

      StringBuilder refLogMessage = new StringBuilder("merge ");
      refLogMessage.append(ref.getName());

      RevCommit headCommit = revWalk.lookupCommit(headId);
      if (revWalk.isMergedInto(srcCommit, headCommit)) {
        return new MergeResult(headCommit, srcCommit, new ObjectId[]{headCommit, srcCommit}, MergeResult.MergeStatus.ALREADY_UP_TO_DATE, mergeStrategy, null);
      }
      else if (revWalk.isMergedInto(headCommit, srcCommit) && fastForwardMode != FastForwardMode.NO_FF) {
        // FAST_FORWARD detected: skip doing a real merge but only update HEAD
        refLogMessage.append(": ").append(MergeResult.MergeStatus.FAST_FORWARD);
        dirCacheCheckout = new DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), srcCommit.getTree());
        dirCacheCheckout.setFailOnConflict(true);
        dirCacheCheckout.checkout();
        String msg = null;
        ObjectId newHead, base;
        MergeResult.MergeStatus mergeStatus;
        if (squash) {
          msg = JGitText.get().squashCommitNotUpdatingHEAD;
          newHead = base = headId;
          mergeStatus = MergeResult.MergeStatus.FAST_FORWARD_SQUASHED;
          List<RevCommit> squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit);
          repository.writeSquashCommitMsg(new SquashMessageFormatter().format(squashedCommits, head));
        }
        else {
          updateHead(refLogMessage, srcCommit, headId, repository);
          newHead = base = srcCommit;
          mergeStatus = MergeResult.MergeStatus.FAST_FORWARD;
        }
        return new MergeResult(newHead, base, new ObjectId[]{headCommit, srcCommit}, mergeStatus, mergeStrategy, null, msg);
      }
      else {
        if (fastForwardMode == FastForwardMode.FF_ONLY) {
          return new MergeResult(headCommit, srcCommit, new ObjectId[]{headCommit, srcCommit}, MergeResult.MergeStatus.ABORTED, mergeStrategy, null);
        }
        String mergeMessage;
        if (squash) {
          mergeMessage = "";
          List<RevCommit> squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit);
          repository.writeSquashCommitMsg(new SquashMessageFormatter().format(squashedCommits, head));
        }
        else {
          mergeMessage = new MergeMessageFormatter().format(Collections.singletonList(ref), head);
          repository.writeMergeCommitMsg(mergeMessage);
          repository.writeMergeHeads(Arrays.asList(ref.getObjectId()));
        }
        Merger merger = mergeStrategy.newMerger(repository);
        boolean noProblems;
        Map<String, org.eclipse.jgit.merge.MergeResult<?>> lowLevelResults = null;
        Map<String, ResolveMerger.MergeFailureReason> failingPaths = null;
        List<String> unmergedPaths = null;
        if (merger instanceof ResolveMerger) {
          ResolveMerger resolveMerger = (ResolveMerger)merger;
          resolveMerger.setCommitNames(new String[]{"BASE", "HEAD", ref.getName()});
          resolveMerger.setWorkingTreeIterator(new FileTreeIterator(repository));
          noProblems = merger.merge(headCommit, srcCommit);
          lowLevelResults = resolveMerger.getMergeResults();
          failingPaths = resolveMerger.getFailingPaths();
          unmergedPaths = resolveMerger.getUnmergedPaths();
        }
        else {
          noProblems = merger.merge(headCommit, srcCommit);
        }
        refLogMessage.append(": Merge made by ");
        if (revWalk.isMergedInto(headCommit, srcCommit)) {
          refLogMessage.append("recursive");
        }
        else {
          refLogMessage.append(mergeStrategy.getName());
        }
        refLogMessage.append('.');
        if (noProblems) {
          dirCacheCheckout = new DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), merger.getResultTreeId());
          dirCacheCheckout.setFailOnConflict(true);
          dirCacheCheckout.checkout();

          String msg = null;
          ObjectId newHeadId = null;
          MergeResult.MergeStatus mergeStatus = null;
          if (!commit && squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED_NOT_COMMITTED;
          }
          if (!commit && !squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_NOT_COMMITTED;
          }
          if (commit && !squash) {
            newHeadId = git.commit().setReflogComment(refLogMessage.toString()).call().getId();
            mergeStatus = MergeResult.MergeStatus.MERGED;
          }
          if (commit && squash) {
            msg = JGitText.get().squashCommitNotUpdatingHEAD;
            newHeadId = headCommit.getId();
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED;
          }
          return new MergeResult(newHeadId, null, new ObjectId[]{headCommit.getId(), srcCommit.getId()}, mergeStatus, mergeStrategy, null, msg);
        }
        else {
          if (failingPaths == null) {
            //noinspection ConstantConditions
            String mergeMessageWithConflicts = new MergeMessageFormatter().formatWithConflicts(mergeMessage, unmergedPaths);
            repository.writeMergeCommitMsg(mergeMessageWithConflicts);
            return new MergeResult(null, merger.getBaseCommitId(), new ObjectId[]{headCommit.getId(), srcCommit.getId()},
                                   MergeResult.MergeStatus.CONFLICTING, mergeStrategy, lowLevelResults);
          }
          else {
            repository.writeMergeCommitMsg(null);
            repository.writeMergeHeads(null);
            return new MergeResult(null, merger.getBaseCommitId(),
                                   new ObjectId[]{headCommit.getId(), srcCommit.getId()},
                                   MergeResult.MergeStatus.FAILED, mergeStrategy,
                                   lowLevelResults, failingPaths, null);
          }
        }
      }
    }
    catch (org.eclipse.jgit.errors.CheckoutConflictException e) {
      throw new CheckoutConflictException(dirCacheCheckout == null ? Collections.<String>emptyList() : dirCacheCheckout.getConflicts(), e);
    }
    finally {
      if (revWalk != null) {
        revWalk.release();
      }
    }
  }

  private static void updateHead(StringBuilder refLogMessage, ObjectId newHeadId, ObjectId oldHeadID, Repository repository) throws IOException, ConcurrentRefUpdateException {
    RefUpdate refUpdate = repository.updateRef(Constants.HEAD);
    refUpdate.setNewObjectId(newHeadId);
    refUpdate.setRefLogMessage(refLogMessage.toString(), false);
    refUpdate.setExpectedOldObjectId(oldHeadID);
    RefUpdate.Result rc = refUpdate.update();
    switch (rc) {
      case NEW:
      case FAST_FORWARD:
        return;
      case REJECTED:
      case LOCK_FAILURE:
        throw new ConcurrentRefUpdateException(
          JGitText.get().couldNotLockHEAD, refUpdate.getRef(), rc);
      default:
        throw new JGitInternalException(MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, newHeadId.toString(), rc));
    }
  }
}