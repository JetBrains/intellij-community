package org.jetbrains.idea.completion.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.jetbrains.idea.completion.api.*
import org.jetbrains.idea.completion.util.logWarn
import kotlin.coroutines.cancellation.CancellationException

private class DependencyCompletionServiceImpl : DependencyCompletionService {
  private val allContributors = DependencyCompletionService.EP_NAME.extensionList

  private fun contributors(request: BaseDependencyCompletionRequest) = allContributors.filter { it.isApplicable(request.context) }

  override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> =
    parallelStream(contributors(request)) { it.search(request) }

  override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<String> =
    parallelStream(contributors(request)) { it.getGroups(request) }
      .distinctUntilChanged()

  override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<String> =
    parallelStream(contributors(request)) { it.getArtifacts(request) }
      .distinctUntilChanged()

  override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<String> =
    parallelStream(contributors(request)) { it.getVersions(request) }
      .distinctUntilChanged()

  private fun <C, R> parallelStream(contributors: List<C>, block: suspend (C) -> List<R>): Flow<R> = channelFlow {
    supervisorScope {
      contributors.forEach { contributor ->
        launch(Dispatchers.IO) {
          try {
            val results = block(contributor)
            for (r in results) {
              send(r)
            }
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (t: Throwable) {
            logWarn(t.message ?: "", t)
          }
        }
      }
    }
  }
}