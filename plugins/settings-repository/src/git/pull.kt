package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.merge.MergeMessageFormatter
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ResolveMerger
import org.eclipse.jgit.merge.SquashMessageFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.RevWalkUtils
import org.eclipse.jgit.transport.FetchResult
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.jetbrains.settingsRepository.*
import java.io.IOException
import java.text.MessageFormat
import java.util.ArrayList

fun wrapIfNeedAndReThrow(e: TransportException) {
  if (e.getStatus() == TransportException.Status.CANNOT_RESOLVE_REPO) {
    throw org.jetbrains.settingsRepository.NoRemoteRepositoryException(e)
  }

  val message = e.getMessage()!!
  if (e.getStatus() == TransportException.Status.NOT_AUTHORIZED || e.getStatus() == TransportException.Status.NOT_PERMITTED ||
      message.contains(JGitText.get().notAuthorized) || message.contains("Auth cancel") || message.contains("Auth fail") || message.contains(": reject HostKey:") /* JSch */) {
    throw AuthenticationException(e)
  }
  else if (e.getStatus() == TransportException.Status.CANCELLED || message == "Download cancelled") {
    throw ProcessCanceledException()
  }
  else {
    throw e
  }
}

open class Pull(val manager: GitRepositoryManager, val indicator: ProgressIndicator) {
  val repository = manager.repository

  // we must use the same StoredConfig instance during the operation
  val config = repository.getConfig()
  val remoteConfig = RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME)

  fun pull(mergeStrategy: MergeStrategy = MergeStrategy.RECURSIVE, commitMessage: String? = null, prefetchedRefToMerge: Ref? = null): UpdateResult? {
    indicator.checkCanceled()

    LOG.debug("Pull")

    val repository = manager.repository
    val repositoryState = repository.getRepositoryState()
    if (repositoryState != RepositoryState.SAFE) {
      LOG.warn(MessageFormat.format(JGitText.get().cannotPullOnARepoWithState, repositoryState.name()))
    }

    val refToMerge = prefetchedRefToMerge ?: fetch()
    if (refToMerge == null) {
      return null
    }

    val mergeResult = merge(refToMerge, mergeStrategy, commitMessage = commitMessage)
    val mergeStatus = mergeResult.mergeStatus
    if (LOG.isDebugEnabled()) {
      LOG.debug(mergeStatus.toString())
    }

    if (mergeStatus == MergeStatus.CONFLICTING) {
      val mergedCommits = mergeResult.mergedCommits
      assert(mergedCommits.size == 2)
      val conflicts = mergeResult.conflicts!!
      var unresolvedFiles = conflictsToVirtualFiles(conflicts)
      val mergeProvider = JGitMergeProvider(repository, mergedCommits[0]!!, mergedCommits[1]!!, conflicts)
      val resolvedFiles: List<VirtualFile>
      val mergedFiles = ArrayList<String>()
      while (true) {
        resolvedFiles = resolveConflicts(unresolvedFiles, mergeProvider)

        for (file in resolvedFiles) {
          mergedFiles.add(file.getPath())
        }

        if (resolvedFiles.size == unresolvedFiles.size) {
          break
        }
        else {
          unresolvedFiles.removeAll(mergedFiles)
        }
      }

      manager.commit()

      return mergeResult.result.toMutable().addChanged(mergedFiles)
    }
    else if (!mergeStatus.isSuccessful()) {
      throw IllegalStateException(mergeResult.toString())
    }
    else {
      return mergeResult.result
    }
  }

  fun fetch(prevRefUpdateResult: RefUpdate.Result? = null): Ref? {
    indicator.checkCanceled()

    val repository = manager.repository

    val transport = Transport.open(repository, remoteConfig)
    val fetchResult: FetchResult
    try {
      transport.setCredentialsProvider(manager.credentialsProvider)
      fetchResult = transport.fetch(indicator.asProgressMonitor(), null)
    }
    catch (e: TransportException) {
      val message = e.getMessage()!!
      if (message.startsWith("Remote does not have ")) {
        LOG.info(message)
        // "Remote does not have refs/heads/master available for fetch." - remote repository is not initialized
        return null
      }

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

    indicator.checkCanceled()

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
        return fetch(refUpdateResult)
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

    val refToMerge = fetchResult.getAdvertisedRef(config.getRemoteBranchFullName())
    if (refToMerge == null) {
      throw IllegalStateException("Could not get advertised ref")
    }
    return refToMerge
  }

  protected fun checkCancelled() {
    indicator.checkCanceled()
  }

  fun merge(unpeeledRef: Ref,
            mergeStrategy: MergeStrategy = MergeStrategy.RECURSIVE,
            commit: Boolean = true,
            fastForwardMode: FastForwardMode = FastForwardMode.FF,
            squash: Boolean = false,
            forceMerge: Boolean = false,
            commitMessage: String? = null): MergeResultEx {
    indicator.checkCanceled()

    val head = repository.getRef(Constants.HEAD)
    if (head == null) {
      throw NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported)
    }

    // Check for FAST_FORWARD, ALREADY_UP_TO_DATE
    val revWalk = RevWalk(repository)
    var dirCacheCheckout: DirCacheCheckout? = null
    try {
      // handle annotated tags
      val ref = repository.peel(unpeeledRef)
      var objectId = ref.getPeeledObjectId()
      if (objectId == null) {
        objectId = ref.getObjectId()
      }

      val srcCommit = revWalk.lookupCommit(objectId)
      val headId = head.getObjectId()
      if (headId == null) {
        revWalk.parseHeaders(srcCommit)
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
        return MergeResultEx(srcCommit, MergeStatus.FAST_FORWARD, arrayOf<ObjectId?>(null, srcCommit), ImmutableUpdateResult(dirCacheCheckout!!.getUpdated().keySet(), dirCacheCheckout!!.getRemoved()))
        //return MergeResult(srcCommit, srcCommit, array(null, srcCommit), MergeStatus.FAST_FORWARD, mergeStrategy, null)
      }

      val refLogMessage = StringBuilder("merge ")
      refLogMessage.append(ref.getName())

      val headCommit = revWalk.lookupCommit(headId)
      if (!forceMerge && revWalk.isMergedInto(srcCommit, headCommit)) {
        return MergeResultEx(headCommit, MergeStatus.ALREADY_UP_TO_DATE, arrayOf<ObjectId?>(headCommit, srcCommit), EMPTY_UPDATE_RESULT)
        //return MergeResult(headCommit, srcCommit, array(headCommit, srcCommit), MergeStatus.ALREADY_UP_TO_DATE, mergeStrategy, null)
      }
      else if (!forceMerge && fastForwardMode != FastForwardMode.NO_FF && revWalk.isMergedInto(headCommit, srcCommit)) {
        // FAST_FORWARD detected: skip doing a real merge but only update HEAD
        refLogMessage.append(": ").append(MergeStatus.FAST_FORWARD)
        dirCacheCheckout = DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), srcCommit.getTree())
        dirCacheCheckout!!.setFailOnConflict(true)
        dirCacheCheckout!!.checkout()
//        var msg: String? = null
        val newHead: ObjectId
//        val base: ObjectId
        val mergeStatus: MergeStatus
        if (squash) {
//          msg = JGitText.get().squashCommitNotUpdatingHEAD
//          base = headId
          newHead = headId
          mergeStatus = MergeStatus.FAST_FORWARD_SQUASHED
          val squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit)
          repository.writeSquashCommitMsg(SquashMessageFormatter().format(squashedCommits, head))
        }
        else {
          updateHead(refLogMessage, srcCommit, headId, repository)
//          base = srcCommit
          newHead = srcCommit
          mergeStatus = MergeStatus.FAST_FORWARD
        }
        return MergeResultEx(newHead, mergeStatus, arrayOf<ObjectId?>(headCommit, srcCommit), ImmutableUpdateResult(dirCacheCheckout!!.getUpdated().keySet(), dirCacheCheckout!!.getRemoved()))
        //return MergeResult(newHead, base, array(headCommit, srcCommit), mergeStatus, mergeStrategy, null, msg)
      }
      else {
        if (fastForwardMode == FastForwardMode.FF_ONLY) {
          return MergeResultEx(headCommit, MergeStatus.ABORTED, arrayOf<ObjectId?>(headCommit, srcCommit), EMPTY_UPDATE_RESULT)
          // return MergeResult(headCommit, srcCommit, array(headCommit, srcCommit), MergeStatus.ABORTED, mergeStrategy, null)
        }

        val mergeMessage: String
        if (squash) {
          mergeMessage = ""
          repository.writeSquashCommitMsg(SquashMessageFormatter().format(RevWalkUtils.find(revWalk, srcCommit, headCommit), head))
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
          merger.setCommitNames(arrayOf("BASE", "HEAD", ref.getName()))
          merger.setWorkingTreeIterator(FileTreeIterator(repository))
          noProblems = merger.merge(headCommit, srcCommit)
          lowLevelResults = merger.getMergeResults()
          failingPaths = merger.getFailingPaths()
          unmergedPaths = merger.getUnmergedPaths()
        }
        else {
          noProblems = merger.merge(headCommit, srcCommit)
        }
        refLogMessage.append(": Merge made by ")
        if (revWalk.isMergedInto(headCommit, srcCommit)) {
          refLogMessage.append("recursive")
        }
        else {
          refLogMessage.append(mergeStrategy.getName())
        }
        refLogMessage.append('.')

        var result: ImmutableUpdateResult? = null
        if (merger is ResolveMerger) {
          result = ImmutableUpdateResult(merger.getToBeCheckedOut().keySet(), merger.getToBeDeleted())
        }

        if (noProblems) {
          // ResolveMerger does checkout
          if (merger !is ResolveMerger) {
            dirCacheCheckout = DirCacheCheckout(repository, headCommit.getTree(), repository.lockDirCache(), merger.getResultTreeId())
            dirCacheCheckout!!.setFailOnConflict(true)
            dirCacheCheckout!!.checkout()
            result = ImmutableUpdateResult(dirCacheCheckout!!.getUpdated().keySet(), dirCacheCheckout!!.getRemoved())
          }

//          var msg: String? = null
          var newHeadId: ObjectId? = null
          var mergeStatus: MergeResult.MergeStatus? = null
          if (!commit && squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED_NOT_COMMITTED
          }
          if (!commit && !squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_NOT_COMMITTED
          }
          if (commit && !squash) {
            newHeadId = manager.commit(commitMessage, refLogMessage.toString()).getId()
            mergeStatus = MergeResult.MergeStatus.MERGED
          }
          if (commit && squash) {
//            msg = JGitText.get().squashCommitNotUpdatingHEAD
            newHeadId = headCommit.getId()
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED
          }
          return MergeResultEx(newHeadId, mergeStatus!!, arrayOf(headCommit.getId(), srcCommit.getId()), result!!)
          // return MergeResult(newHeadId, null, array(headCommit.getId(), srcCommit.getId()), mergeStatus, mergeStrategy, null, msg)
        }
        else {
          if (failingPaths == null) {
            val mergeMessageWithConflicts = MergeMessageFormatter().formatWithConflicts(mergeMessage, unmergedPaths)
            repository.writeMergeCommitMsg(mergeMessageWithConflicts)
            return MergeResultEx(null, MergeResult.MergeStatus.CONFLICTING, arrayOf(headCommit.getId(), srcCommit.getId()), result!!, lowLevelResults)
            //return MergeResult(null, merger.getBaseCommitId(), array(headCommit.getId(), srcCommit.getId()), MergeResult.MergeStatus.CONFLICTING, mergeStrategy, lowLevelResults)
          }
          else {
            repository.writeMergeCommitMsg(null)
            repository.writeMergeHeads(null)
            return MergeResultEx(null, MergeResult.MergeStatus.FAILED, arrayOf(headCommit.getId(), srcCommit.getId()), result!!, lowLevelResults)
            //return MergeResult(null, merger.getBaseCommitId(), array(headCommit.getId(), srcCommit.getId()), MergeResult.MergeStatus.FAILED, mergeStrategy, lowLevelResults, failingPaths, null)
          }
        }
      }
    }
    catch (e: org.eclipse.jgit.errors.CheckoutConflictException) {
      throw CheckoutConflictException(if (dirCacheCheckout == null) listOf<String>() else dirCacheCheckout!!.getConflicts(), e)
    }
    finally {
      revWalk.release()
    }
  }
}

class MergeResultEx(val newHead: ObjectId?, val mergeStatus: MergeStatus, val mergedCommits: Array<ObjectId?>, val result: ImmutableUpdateResult, val conflicts: Map<String, org.eclipse.jgit.merge.MergeResult<*>>? = null)

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

