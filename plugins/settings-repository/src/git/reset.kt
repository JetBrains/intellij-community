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
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.merge.MergeStrategy
import org.jetbrains.jgit.dirCache.deleteAllFiles
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.MutableUpdateResult
import org.jetbrains.settingsRepository.UpdateResult

internal class Reset(manager: GitRepositoryManager, indicator: ProgressIndicator) : Pull(manager, indicator) {
  fun reset(toTheirs: Boolean, localRepositoryInitializer: (() -> Unit)? = null): UpdateResult {
    val message = if (toTheirs) "Overwrite local to ${manager.getUpstream()}" else "Overwrite remote ${manager.getUpstream()} to local"
    LOG.debug { message }

    val resetResult = repository.resetHard()
    val result = MutableUpdateResult(resetResult.updated.keys, resetResult.removed)

    indicator?.checkCanceled()

    val commitMessage = commitMessageFormatter.message(message)
    // grab added/deleted/renamed/modified files
    val mergeStrategy = if (toTheirs) MergeStrategy.THEIRS else MergeStrategy.OURS

    var refToMerge = fetch()
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
      manager.commit(indicator)
    }
    return result
  }
}