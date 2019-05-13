// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.merge.MergeStrategy
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.MutableUpdateResult
import org.jetbrains.settingsRepository.UpdateResult

internal class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  suspend fun reset(toTheirs: Boolean, localRepositoryInitializer: (() -> Unit)? = null): UpdateResult {
    val message = if (toTheirs) "Overwrite local to ${manager.repository.upstream}" else "Overwrite remote ${manager.repository.upstream} to local"
    LOG.debug { message }

    val resetResult = repository.resetHard()
    val result = MutableUpdateResult(resetResult.updated.keys, resetResult.removed)

    indicator?.checkCanceled()

    val commitMessage = commitMessageFormatter.message(message)
    // grab added/deleted/renamed/modified files
    val mergeStrategy = if (toTheirs) MergeStrategy.THEIRS else MergeStrategy.OURS

    val refToMerge = fetch()
    val mergeResult = if (refToMerge == null) null else merge(refToMerge, mergeStrategy, forceMerge = true, commitMessage = commitMessage)
    val firstMergeResult = mergeResult?.result

    if (!toTheirs && mergeResult?.status == MergeResult.MergeStatus.FAST_FORWARD && firstMergeResult!!.changed.isNotEmpty()) {
      // we specify forceMerge, so if we get FAST_FORWARD it means that our repo doesn't have HEAD (empty) and we must delete all local files to revert all remote updates
      repository.deleteAllFiles()
      result.deleted.addAll(firstMergeResult.changed)
      repository.commit(commitMessage)
    }

    if (localRepositoryInitializer == null) {
      if (firstMergeResult == null) {
        // nothing to merge, so, we merge latest origin commit
        val fetchRefSpecs = remoteConfig.fetchRefSpecs
        assert(fetchRefSpecs.size == 1)

        val latestUpstreamCommit = repository.findRef(fetchRefSpecs[0].destination!!)
        if (latestUpstreamCommit == null) {
          if (toTheirs) {
            repository.deleteAllFiles(result.deleted)
            result.changed.removeAll(result.deleted)
            repository.commit(commitMessage)
          }
          else {
            LOG.debug("uninitialized remote (empty) - we don't need to merge")
          }
          return result
        }

        if (repository.repositoryState == RepositoryState.MERGING) {
          repository.resetHard()
        }

        val secondMergeResult = merge(latestUpstreamCommit, mergeStrategy, true, forceMerge = true, commitMessage = commitMessage)
        if (!secondMergeResult.status.isSuccessful) {
          throw IllegalStateException(secondMergeResult.toString())
        }
        result.add(secondMergeResult.result)
      }
      else {
        result.add(firstMergeResult)
      }
    }
    else {
      assert(!toTheirs)

      result.add(firstMergeResult)

      // must be performed only after initial pull, so, local changes will be relative to remote files
      localRepositoryInitializer()
      (manager as GitRepositoryManager).commit(indicator)
    }
    return result
  }
}