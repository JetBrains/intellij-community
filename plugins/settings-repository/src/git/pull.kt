package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.RevWalkUtils
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.jetbrains.plugins.settingsRepository.AuthenticationException

import java.io.IOException
import java.text.MessageFormat
import java.util.*

import org.jetbrains.plugins.settingsRepository.BaseRepositoryManager.LOG
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.merge.MergeConfig
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.SquashMessageFormatter
import org.eclipse.jgit.merge.MergeMessageFormatter
import org.eclipse.jgit.merge.ResolveMerger

fun wrapIfNeedAndReThrow(e: TransportException) {
  val message = e.getMessage()!!
  if (message.contains(JGitText.get().notAuthorized) || message.contains("Auth cancel") || message.contains("Auth fail") /* JSch */) {
    throw AuthenticationException(message, e)
  }
  else {
    throw e
  }
}

fun pull(manager: GitRepositoryManager, indicator: ProgressIndicator) {
  LOG.debug("Pull")

  val mergeResult = fetch(manager, indicator, null)
  if (mergeResult == null) {
    return
  }

  val mergeStatus = mergeResult.getMergeStatus()
  if (LOG.isDebugEnabled()) {
    LOG.debug(mergeStatus.toString())
  }
  if (!mergeStatus.isSuccessful()) {
    throw UnsupportedOperationException()
  }
}

private fun fetch(manager: GitRepositoryManager, indicator: ProgressIndicator, prevRefUpdateResult: RefUpdate.Result?): MergeResult? {
  val repository = manager.git.getRepository()
  val repositoryState = repository.getRepositoryState()
  if (repositoryState != RepositoryState.SAFE) {
    LOG.warn(MessageFormat.format(JGitText.get().cannotPullOnARepoWithState, repositoryState.name()))
  }

  // we must use the same StoredConfig instance during the operation
  val config = repository.getConfig()
  val remoteConfig = RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME)
  val transport = Transport.open(repository, remoteConfig)
  val fetchResult: FetchResult
  try {
    transport.setCredentialsProvider(manager.credentialsProvider)
    fetchResult = transport.fetch(JGitProgressMonitor(indicator), null)
  }
  catch (e: TransportException) {
    wrapIfNeedAndReThrow(e)
    return null
  }
  finally {
    transport.close()
  }

  if (LOG.isDebugEnabled()) {
    printMessages(fetchResult)
    for (refUpdate in fetchResult.getTrackingRefUpdates()) {
      LOG.debug(refUpdate.toString())
    }
  }

  var hasChanges = false
  for (fetchRefSpec in remoteConfig.getFetchRefSpecs()) {
    val refUpdate = fetchResult.getTrackingRefUpdate(fetchRefSpec.getDestination())
    if (refUpdate == null) {
      LOG.debug("No ref update for " + fetchRefSpec)
      continue
    }

    val refUpdateResult = refUpdate.getResult()
    // we can have more than one fetch ref spec, but currently we don't worry about it
    if (refUpdateResult == RefUpdate.Result.LOCK_FAILURE || refUpdateResult == RefUpdate.Result.IO_FAILURE) {
      if (prevRefUpdateResult == refUpdateResult) {
        throw IOException("Ref update result " + refUpdateResult.name() + ", we have already tried to fetch again, but no luck")
      }

      LOG.warn("Ref update result " + refUpdateResult.name() + ", trying again after 500 ms")
      Thread.sleep(500)
      return fetch(manager, indicator, refUpdateResult)
    }

    if (!(refUpdateResult == RefUpdate.Result.FAST_FORWARD || refUpdateResult == RefUpdate.Result.NEW || refUpdateResult == RefUpdate.Result.FORCED)) {
      throw UnsupportedOperationException("Unsupported ref update result")
    }

    if (!hasChanges) {
      hasChanges = refUpdateResult != RefUpdate.Result.NO_CHANGE
    }
  }

  if (!hasChanges) {
    LOG.debug("No remote changes")
    return null
  }

  val remoteBranchFullName = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE)
  if (StringUtil.isEmpty(remoteBranchFullName)) {
    throw IllegalStateException("branch.master.merge refspec must be specified")
  }

  val refToMerge = fetchResult.getAdvertisedRef(remoteBranchFullName)
  if (refToMerge == null) {
    throw IllegalStateException("Could not get advertised ref")
  }
  return merge(config, manager.git, refToMerge)
}

fun merge(config: StoredConfig, git: Git, unpeeledRef: Ref): MergeResult {
  val repository = git.getRepository()

  val head = repository.getRef(Constants.HEAD)
  if (head == null) {
    throw NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported)
  }

  val mergeConfig = config.get<MergeConfig>(MergeConfig.getParser(Constants.MASTER))
  val squash = mergeConfig.isSquash()
  val commit = mergeConfig.isCommit()
  val fastForwardMode = mergeConfig.getFastForwardMode()

  if (squash && fastForwardMode == FastForwardMode.NO_FF) {
    throw JGitInternalException(JGitText.get().cannotCombineSquashWithNoff)
  }

  val mergeStrategy = MergeStrategy.RECURSIVE

  var revWalk: RevWalk? = null
  var dirCacheCheckout: DirCacheCheckout? = null
  try {
    // Check for FAST_FORWARD, ALREADY_UP_TO_DATE
    revWalk = RevWalk(repository)

    // handle annotated tags
    val ref = repository.peel(unpeeledRef)
    var objectId: ObjectId? = ref.getPeeledObjectId()
    if (objectId == null) {
      objectId = ref.getObjectId()
    }

    val srcCommit = revWalk!!.lookupCommit(objectId)
    val headId = head.getObjectId()
    if (headId == null) {
      revWalk!!.parseHeaders(srcCommit)
      dirCacheCheckout = DirCacheCheckout(repository, repository.lockDirCache(), srcCommit.getTree())
      dirCacheCheckout!!.setFailOnConflict(true)
      dirCacheCheckout!!.checkout()
      val refUpdate = repository.updateRef(head.getTarget().getName())
      refUpdate.setNewObjectId(objectId)
      refUpdate.setExpectedOldObjectId(null)
      refUpdate.setRefLogMessage("initial pull", false)
      if (refUpdate.update() != RefUpdate.Result.NEW) {
        throw NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported)
      }
      return MergeResult(srcCommit, srcCommit, array(null, srcCommit), MergeStatus.FAST_FORWARD, mergeStrategy, null)
    }

    val refLogMessage = StringBuilder("merge ")
    refLogMessage.append(ref.getName())

    val headCommit = revWalk!!.lookupCommit(headId)
    if (revWalk!!.isMergedInto(srcCommit, headCommit)) {
      return MergeResult(headCommit, srcCommit, array<ObjectId>(headCommit, srcCommit), MergeStatus.ALREADY_UP_TO_DATE, mergeStrategy, null)
    }
    else if (revWalk!!.isMergedInto(headCommit, srcCommit) && fastForwardMode != FastForwardMode.NO_FF) {
      // FAST_FORWARD detected: skip doing a real merge but only update HEAD
      refLogMessage.append(": ").append(MergeStatus.FAST_FORWARD)
      dirCacheCheckout = DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), srcCommit.getTree())
      dirCacheCheckout!!.setFailOnConflict(true)
      dirCacheCheckout!!.checkout()
      var msg: String? = null
      val newHead: ObjectId
      val base: ObjectId
      val mergeStatus: MergeStatus
      if (squash) {
        msg = JGitText.get().squashCommitNotUpdatingHEAD
        base = headId
        newHead = headId
        mergeStatus = MergeStatus.FAST_FORWARD_SQUASHED
        val squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit)
        repository.writeSquashCommitMsg(SquashMessageFormatter().format(squashedCommits, head))
      }
      else {
        updateHead(refLogMessage, srcCommit, headId, repository)
        base = srcCommit
        newHead = srcCommit
        mergeStatus = MergeStatus.FAST_FORWARD
      }
      return MergeResult(newHead, base, array<ObjectId>(headCommit, srcCommit), mergeStatus, mergeStrategy, null, msg)
    }
    else {
      if (fastForwardMode == FastForwardMode.FF_ONLY) {
        return MergeResult(headCommit, srcCommit, array<ObjectId>(headCommit, srcCommit), MergeStatus.ABORTED, mergeStrategy, null)
      }
      val mergeMessage: String
      if (squash) {
        mergeMessage = ""
        val squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit)
        repository.writeSquashCommitMsg(SquashMessageFormatter().format(squashedCommits, head))
      }
      else {
        mergeMessage = MergeMessageFormatter().format(listOf(ref), head)
        repository.writeMergeCommitMsg(mergeMessage)
        repository.writeMergeHeads(arrayListOf(ref.getObjectId()))
      }
      val merger = mergeStrategy.newMerger(repository)
      val noProblems: Boolean
      var lowLevelResults: Map<String, org.eclipse.jgit.merge.MergeResult<*>>? = null
      var failingPaths: Map<String, ResolveMerger.MergeFailureReason>? = null
      var unmergedPaths: List<String>? = null
      if (merger is ResolveMerger) {
        val resolveMerger = merger as ResolveMerger
        resolveMerger.setCommitNames(array("BASE", "HEAD", ref.getName()))
        resolveMerger.setWorkingTreeIterator(FileTreeIterator(repository))
        noProblems = merger.merge(headCommit, srcCommit)
        lowLevelResults = resolveMerger.getMergeResults()
        failingPaths = resolveMerger.getFailingPaths()
        unmergedPaths = resolveMerger.getUnmergedPaths()
      }
      else {
        noProblems = merger.merge(headCommit, srcCommit)
      }
      refLogMessage.append(": Merge made by ")
      if (revWalk!!.isMergedInto(headCommit, srcCommit)) {
        refLogMessage.append("recursive")
      }
      else {
        refLogMessage.append(mergeStrategy.getName())
      }
      refLogMessage.append('.')
      if (noProblems) {
        dirCacheCheckout = DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), merger.getResultTreeId())
        dirCacheCheckout!!.setFailOnConflict(true)
        dirCacheCheckout!!.checkout()

        var msg: String? = null
        var newHeadId: ObjectId? = null
        var mergeStatus: MergeResult.MergeStatus? = null
        if (!commit && squash) {
          mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED_NOT_COMMITTED
        }
        if (!commit && !squash) {
          mergeStatus = MergeResult.MergeStatus.MERGED_NOT_COMMITTED
        }
        if (commit && !squash) {
          newHeadId = git.commit().setReflogComment(refLogMessage.toString()).call().getId()
          mergeStatus = MergeResult.MergeStatus.MERGED
        }
        if (commit && squash) {
          msg = JGitText.get().squashCommitNotUpdatingHEAD
          newHeadId = headCommit.getId()
          mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED
        }
        return MergeResult(newHeadId, null, array(headCommit.getId(), srcCommit.getId()), mergeStatus, mergeStrategy, null, msg)
      }
      else {
        if (failingPaths == null) {
          //noinspection ConstantConditions
          val mergeMessageWithConflicts = MergeMessageFormatter().formatWithConflicts(mergeMessage, unmergedPaths)
          repository.writeMergeCommitMsg(mergeMessageWithConflicts)
          return MergeResult(null, merger.getBaseCommitId(), array(headCommit.getId(), srcCommit.getId()), MergeResult.MergeStatus.CONFLICTING, mergeStrategy, lowLevelResults)
        }
        else {
          repository.writeMergeCommitMsg(null)
          repository.writeMergeHeads(null)
          return MergeResult(null, merger.getBaseCommitId(), array(headCommit.getId(), srcCommit.getId()), MergeResult.MergeStatus.FAILED, mergeStrategy, lowLevelResults, failingPaths, null)
        }
      }
    }
  }
  catch (e: org.eclipse.jgit.errors.CheckoutConflictException) {
    throw CheckoutConflictException(if (dirCacheCheckout == null) listOf<String>() else dirCacheCheckout!!.getConflicts(), e)
  }
  finally {
    if (revWalk != null) {
      revWalk!!.release()
    }
  }
}

private fun updateHead(refLogMessage: StringBuilder, newHeadId: ObjectId, oldHeadID: ObjectId, repository: Repository) {
  val refUpdate = repository.updateRef(Constants.HEAD)
  refUpdate.setNewObjectId(newHeadId)
  refUpdate.setRefLogMessage(refLogMessage.toString(), false)
  refUpdate.setExpectedOldObjectId(oldHeadID)
  val rc = refUpdate.update()
  when (rc) {
    RefUpdate.Result.NEW, RefUpdate.Result.FAST_FORWARD -> return
    RefUpdate.Result.REJECTED, RefUpdate.Result.LOCK_FAILURE -> throw ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD, refUpdate.getRef(), rc)
    else -> throw JGitInternalException(MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, newHeadId.toString(), rc))
  }
}

