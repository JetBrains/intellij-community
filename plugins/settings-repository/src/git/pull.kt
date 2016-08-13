/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.containers.hash.LinkedHashMap
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.Sequence
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.merge.MergeMessageFormatter
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ResolveMerger
import org.eclipse.jgit.merge.SquashMessageFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.RevWalkUtils
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.jetbrains.settingsRepository.*
import java.io.IOException
import java.text.MessageFormat

open internal class Pull(val manager: GitRepositoryManager, val indicator: ProgressIndicator?, val commitMessageFormatter: CommitMessageFormatter = IdeaCommitMessageFormatter()) {
  val repository = manager.repository

  // we must use the same StoredConfig instance during the operation
  val config = repository.config
  val remoteConfig = RemoteConfig(config, Constants.DEFAULT_REMOTE_NAME)

  fun pull(mergeStrategy: MergeStrategy = MergeStrategy.RECURSIVE, commitMessage: String? = null, prefetchedRefToMerge: Ref? = null): UpdateResult? {
    indicator?.checkCanceled()

    LOG.debug("Pull")

    val state = repository.fixAndGetState()
    if (!state.canCheckout()) {
      LOG.error("Cannot pull, repository in state ${state.description}")
      return null
    }

    val refToMerge = prefetchedRefToMerge ?: fetch() ?: return null
    val mergeResult = merge(refToMerge, mergeStrategy, commitMessage = commitMessage)
    val mergeStatus = mergeResult.status
    LOG.debug { mergeStatus.toString() }

    if (mergeStatus == MergeStatus.CONFLICTING) {
      return resolveConflicts(mergeResult, repository)
    }
    else if (!mergeStatus.isSuccessful) {
      throw IllegalStateException(mergeResult.toString())
    }
    else {
      return mergeResult.result
    }
  }

  fun fetch(prevRefUpdateResult: RefUpdate.Result? = null): Ref? {
    indicator?.checkCanceled()

    val fetchResult = repository.fetch(remoteConfig, manager.credentialsProvider, indicator.asProgressMonitor()) ?: return null

    if (LOG.isDebugEnabled) {
      printMessages(fetchResult)
      for (refUpdate in fetchResult.trackingRefUpdates) {
        LOG.debug(refUpdate.toString())
      }
    }

    indicator?.checkCanceled()

    var hasChanges = false
    for (fetchRefSpec in remoteConfig.fetchRefSpecs) {
      val refUpdate = fetchResult.getTrackingRefUpdate(fetchRefSpec.destination)
      if (refUpdate == null) {
        LOG.debug("No ref update for $fetchRefSpec")
        continue
      }

      val refUpdateResult = refUpdate.result
      // we can have more than one fetch ref spec, but currently we don't worry about it
      if (refUpdateResult == RefUpdate.Result.LOCK_FAILURE || refUpdateResult == RefUpdate.Result.IO_FAILURE) {
        if (prevRefUpdateResult == refUpdateResult) {
          throw IOException("Ref update result ${refUpdateResult.name}, we have already tried to fetch again, but no luck")
        }

        LOG.warn("Ref update result ${refUpdateResult.name}, trying again after 500 ms")
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

    return fetchResult.getAdvertisedRef(config.getRemoteBranchFullName()) ?: throw IllegalStateException("Could not get advertised ref")
  }

  fun merge(unpeeledRef: Ref,
            mergeStrategy: MergeStrategy = MergeStrategy.RECURSIVE,
            commit: Boolean = true,
            fastForwardMode: FastForwardMode = FastForwardMode.FF,
            squash: Boolean = false,
            forceMerge: Boolean = false,
            commitMessage: String? = null): MergeResultEx {
    indicator?.checkCanceled()

    val head = repository.findRef(Constants.HEAD) ?: throw NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported)

    // handle annotated tags
    val ref = repository.peel(unpeeledRef)
    val objectId = ref.peeledObjectId ?: ref.objectId
    // Check for FAST_FORWARD, ALREADY_UP_TO_DATE
    val revWalk = RevWalk(repository)
    var dirCacheCheckout: DirCacheCheckout? = null
    try {
      val srcCommit = revWalk.lookupCommit(objectId)
      val headId = head.objectId
      if (headId == null) {
        revWalk.parseHeaders(srcCommit)
        dirCacheCheckout = DirCacheCheckout(repository, repository.lockDirCache(), srcCommit.tree)
        dirCacheCheckout.setFailOnConflict(true)
        dirCacheCheckout.checkout()
        val refUpdate = repository.updateRef(head.target.name)
        refUpdate.setNewObjectId(objectId)
        refUpdate.setExpectedOldObjectId(null)
        refUpdate.setRefLogMessage("initial pull", false)
        if (refUpdate.update() != RefUpdate.Result.NEW) {
          throw NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported)
        }
        return MergeResultEx(MergeStatus.FAST_FORWARD, arrayOf(null, srcCommit), ImmutableUpdateResult(dirCacheCheckout.updated.keys, dirCacheCheckout.removed))
      }

      val refLogMessage = StringBuilder("merge ")
      refLogMessage.append(ref.name)

      val headCommit = revWalk.lookupCommit(headId)
      if (!forceMerge && revWalk.isMergedInto(srcCommit, headCommit)) {
        return MergeResultEx(MergeStatus.ALREADY_UP_TO_DATE, arrayOf<ObjectId?>(headCommit, srcCommit), EMPTY_UPDATE_RESULT)
      }
      else if (!forceMerge && fastForwardMode != FastForwardMode.NO_FF && revWalk.isMergedInto(headCommit, srcCommit)) {
        // FAST_FORWARD detected: skip doing a real merge but only update HEAD
        refLogMessage.append(": ").append(MergeStatus.FAST_FORWARD)
        dirCacheCheckout = DirCacheCheckout(repository, headCommit.tree, repository.lockDirCache(), srcCommit.tree)
        dirCacheCheckout.setFailOnConflict(true)
        dirCacheCheckout.checkout()
        val mergeStatus: MergeStatus
        if (squash) {
          mergeStatus = MergeStatus.FAST_FORWARD_SQUASHED
          val squashedCommits = RevWalkUtils.find(revWalk, srcCommit, headCommit)
          repository.writeSquashCommitMsg(SquashMessageFormatter().format(squashedCommits, head))
        }
        else {
          updateHead(refLogMessage, srcCommit, headId, repository)
          mergeStatus = MergeStatus.FAST_FORWARD
        }
        return MergeResultEx(mergeStatus, arrayOf<ObjectId?>(headCommit, srcCommit), ImmutableUpdateResult(dirCacheCheckout.updated.keys, dirCacheCheckout.removed))
      }
      else {
        if (fastForwardMode == FastForwardMode.FF_ONLY) {
          return MergeResultEx(MergeStatus.ABORTED, arrayOf<ObjectId?>(headCommit, srcCommit), EMPTY_UPDATE_RESULT)
        }

        val mergeMessage: String
        if (squash) {
          mergeMessage = ""
          repository.writeSquashCommitMsg(SquashMessageFormatter().format(RevWalkUtils.find(revWalk, srcCommit, headCommit), head))
        }
        else {
          mergeMessage = commitMessageFormatter.mergeMessage(listOf(ref), head)
          repository.writeMergeCommitMsg(mergeMessage)
          repository.writeMergeHeads(listOf(ref.objectId))
        }
        val merger = mergeStrategy.newMerger(repository)
        val noProblems: Boolean
        var lowLevelResults: Map<String, org.eclipse.jgit.merge.MergeResult<out Sequence>>? = null
        var failingPaths: Map<String, ResolveMerger.MergeFailureReason>? = null
        var unmergedPaths: List<String>? = null
        if (merger is ResolveMerger) {
          merger.commitNames = arrayOf("BASE", "HEAD", ref.name)
          merger.setWorkingTreeIterator(FileTreeIterator(repository))
          noProblems = merger.merge(headCommit, srcCommit)
          lowLevelResults = merger.mergeResults
          failingPaths = merger.failingPaths
          unmergedPaths = merger.unmergedPaths
        }
        else {
          noProblems = merger.merge(headCommit, srcCommit)
        }
        refLogMessage.append(": Merge made by ")
        refLogMessage.append(if (revWalk.isMergedInto(headCommit, srcCommit)) "recursive" else mergeStrategy.name)
        refLogMessage.append('.')

        var result = if (merger is ResolveMerger) ImmutableUpdateResult(merger.toBeCheckedOut.keys, merger.toBeDeleted) else null
        if (noProblems) {
          // ResolveMerger does checkout
          if (merger !is ResolveMerger) {
            dirCacheCheckout = DirCacheCheckout(repository, headCommit.tree, repository.lockDirCache(), merger.resultTreeId)
            dirCacheCheckout.setFailOnConflict(true)
            dirCacheCheckout.checkout()
            result = ImmutableUpdateResult(dirCacheCheckout.updated.keys, dirCacheCheckout.removed)
          }

          var mergeStatus: MergeResult.MergeStatus? = null
          if (!commit && squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED_NOT_COMMITTED
          }
          if (!commit && !squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_NOT_COMMITTED
          }
          if (commit && !squash) {
            repository.commit(commitMessage, refLogMessage.toString()).id
            mergeStatus = MergeResult.MergeStatus.MERGED
          }
          if (commit && squash) {
            mergeStatus = MergeResult.MergeStatus.MERGED_SQUASHED
          }
          return MergeResultEx(mergeStatus!!, arrayOf(headCommit.id, srcCommit.id), result!!)
        }
        else if (failingPaths == null) {
          repository.writeMergeCommitMsg(MergeMessageFormatter().formatWithConflicts(mergeMessage, unmergedPaths))
          return MergeResultEx(MergeResult.MergeStatus.CONFLICTING, arrayOf(headCommit.id, srcCommit.id), result!!, lowLevelResults)
        }
        else {
          repository.writeMergeCommitMsg(null)
          repository.writeMergeHeads(null)
          return MergeResultEx(MergeResult.MergeStatus.FAILED, arrayOf(headCommit.id, srcCommit.id), result!!, lowLevelResults)
        }
      }
    }
    catch (e: org.eclipse.jgit.errors.CheckoutConflictException) {
      throw CheckoutConflictException(dirCacheCheckout?.conflicts ?: listOf(), e)
    }
    finally {
      revWalk.close()
    }
  }
}

class MergeResultEx(val status: MergeStatus, val mergedCommits: Array<ObjectId?>, val result: ImmutableUpdateResult, val conflicts: Map<String, org.eclipse.jgit.merge.MergeResult<out Sequence>>? = null)

private fun updateHead(refLogMessage: StringBuilder, newHeadId: ObjectId, oldHeadID: ObjectId, repository: Repository) {
  val refUpdate = repository.updateRef(Constants.HEAD)
  refUpdate.setNewObjectId(newHeadId)
  refUpdate.setRefLogMessage(refLogMessage.toString(), false)
  refUpdate.setExpectedOldObjectId(oldHeadID)
  val rc = refUpdate.update()
  when (rc) {
    RefUpdate.Result.NEW, RefUpdate.Result.FAST_FORWARD -> return
    RefUpdate.Result.REJECTED, RefUpdate.Result.LOCK_FAILURE -> throw ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD, refUpdate.ref, rc)
    else -> throw JGitInternalException(MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, newHeadId.toString(), rc))
  }
}

private fun resolveConflicts(mergeResult: MergeResultEx, repository: Repository): MutableUpdateResult {
  assert(mergeResult.mergedCommits.size == 2)
  val conflicts = mergeResult.conflicts!!
  val mergeProvider = JGitMergeProvider(repository, conflicts, { path, index ->
    val rawText = get(path)!!.sequences.get(index) as RawText
    // RawText.EMPTY_TEXT if content is null - deleted
    if (rawText == RawText.EMPTY_TEXT) null else rawText.content
  })
  val mergedFiles = resolveConflicts(mergeProvider, conflictsToVirtualFiles(conflicts), repository)
  return mergeResult.result.toMutable().addChanged(mergedFiles)
}

private fun resolveConflicts(mergeProvider: JGitMergeProvider<out Any>, unresolvedFiles: MutableList<VirtualFile>, repository: Repository): List<String> {
  val mergedFiles = SmartList<String>()
  while (true) {
    val resolvedFiles = resolveConflicts(unresolvedFiles, mergeProvider)

    for (file in resolvedFiles) {
      mergedFiles.add(file.path)
    }

    if (resolvedFiles.size == unresolvedFiles.size) {
      break
    }
    else {
      unresolvedFiles.removeAll { it.path in mergedFiles }
    }
  }

  // merge commit template will be used, so, we don't have to specify commit message explicitly
  repository.commit()

  return mergedFiles
}

internal fun Repository.fixAndGetState(): RepositoryState {
  var state = repositoryState
  if (state == RepositoryState.MERGING) {
    resolveUnmergedConflicts(this)
    // compute new state
    state = repositoryState
  }
  return state
}

internal fun resolveUnmergedConflicts(repository: Repository) {
  val conflicts = LinkedHashMap<String, Array<ByteArray?>>()
  repository.newObjectReader().use { reader ->
    val dirCache = repository.readDirCache()
    for (i in 0..(dirCache.entryCount - 1)) {
      val entry = dirCache.getEntry(i)
      if (!entry.isMerged) {
        conflicts.getOrPut(entry.pathString, { arrayOfNulls<ByteArray>(3) })[entry.stage - 1] = reader.open(entry.objectId, Constants.OBJ_BLOB).cachedBytes
      }
    }
  }

  resolveConflicts(JGitMergeProvider(repository, conflicts, { path, index -> get(path)!!.get(index) }), conflictsToVirtualFiles(conflicts), repository)
}