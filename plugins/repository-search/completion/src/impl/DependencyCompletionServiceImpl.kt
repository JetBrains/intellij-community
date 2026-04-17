package com.intellij.repository.search.completion.impl

import com.intellij.repository.search.completion.api.BaseDependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.intellij.repository.search.completion.util.logWarn
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import kotlin.coroutines.cancellation.CancellationException

internal class DependencyCompletionServiceImpl : DependencyCompletionService {
  private val allContributors get() = DependencyCompletionService.EP_NAME.extensionList

  private fun contributors(request: BaseDependencyCompletionRequest) = allContributors.filter {
    it.isEnabled()
    && it.buildSystemId == request.context.buildSystemId
  }

  override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> =
    parallelStream(contributors(request)) { it.search(request) }

  override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyPartCompletionResult> =
    parallelStream(contributors(request)) { it.getGroups(request) }
      .distinctUntilChanged()

  override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyPartCompletionResult> =
    parallelStream(contributors(request)) { it.getArtifacts(request) }
      .distinctUntilChanged()

  override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyPartCompletionResult> =
    parallelStream(contributors(request)) { it.getVersions(request) }
      .distinctUntilChanged()

  private fun <C : DependencyCompletionContributor, R> parallelStream(
    contributors: List<C>,
    block: suspend (C) -> List<R>,
  ): Flow<R> {
    val serverContributors = contributors.filter { it.source == DependencyCompletionContributionSource.SERVER }
    if (serverContributors.isEmpty()) return simpleParallelStream(contributors, block)

    val localContributors = contributors.filter { it.source != DependencyCompletionContributionSource.SERVER }
    val localDelay = Registry.intValue("dependency.completion.local.results.delay.ms", 3000).milliseconds

    return channelFlow {
      supervisorScope {
        val serverDone = CompletableDeferred<Unit>()

        val serverJobs = serverContributors.map { contributor ->
          launch(Dispatchers.IO) {
            try {
              block(contributor).forEach { send(it) }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (t: Throwable) {
              logWarn(t.message ?: "", t)
            }
          }
        }

        launch {
          serverJobs.joinAll()
          serverDone.complete(Unit)
        }

        localContributors.forEach { contributor ->
          launch(Dispatchers.IO) {
            try {
              val results = block(contributor)
              withTimeoutOrNull(localDelay) { serverDone.await() }
              results.forEach { send(it) }
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

  private fun <C, R> simpleParallelStream(contributors: List<C>, block: suspend (C) -> List<R>): Flow<R> = channelFlow {
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