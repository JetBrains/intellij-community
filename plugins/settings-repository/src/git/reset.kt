package org.jetbrains.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.merge.MergeStrategy
import org.jetbrains.jgit.dirCache.deleteAllFiles
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.MutableUpdateResult
import org.jetbrains.settingsRepository.UpdateResult

class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  fun reset(toTheirs: Boolean, localRepositoryInitializer: (() -> Unit)? = null): UpdateResult {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Reset to ${if (toTheirs) "theirs" else "my"}")
    }

    val resetResult = repository.resetHard()
    val result = MutableUpdateResult(resetResult.getUpdated().keySet(), resetResult.getRemoved())

    indicator.checkCanceled()

    val commitMessage = "Reset to ${if (toTheirs) manager.getUpstream() else "my"}"
    // grab added/deleted/renamed/modified files
    val mergeStrategy = if (toTheirs) MergeStrategy.THEIRS else MergeStrategy.OURS
    val firstMergeResult = pull(mergeStrategy, commitMessage)

    if (localRepositoryInitializer == null) {
      if (firstMergeResult == null) {
        // nothing to merge, so, we merge latest origin commit
        val fetchRefSpecs = remoteConfig.getFetchRefSpecs()
        assert(fetchRefSpecs.size() == 1)

        val latestUpstreamCommit = repository.getRef(fetchRefSpecs[0].getDestination()!!)
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

        val mergeResult = merge(latestUpstreamCommit, mergeStrategy, true, forceMerge = true, commitMessage = commitMessage)
        if (!mergeResult.mergeStatus.isSuccessful()) {
          throw IllegalStateException(mergeResult.toString())
        }
        result.add(mergeResult.result)
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
      manager.commit(indicator)
      result.add(pull(mergeStrategy, commitMessage))
    }
    return result
  }
}