package com.intellij.repository.search.completion.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.BaseDependencyCompletionRequest
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.util.logWarn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal class DependencyCompletionServiceImpl : DependencyCompletionService {
  private val allContributors get() = DependencyCompletionService.EP_NAME.extensionList

  private fun contributors(request: BaseDependencyCompletionRequest) = allContributors.filter {
    it.isEnabled()
    && it.buildSystemId == request.context.buildSystemId
  }

  override fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> =
    parallelStream(contributors(request)) { it.search(request) }
      .distinctItemsBy { listOf(it.groupId, it.artifactId, it.version, it.scope) }

  override fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    parallelStream(contributors(request)) { it.getGroups(request) }
      .distinctItemsBy { it.result }

  override fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    parallelStream(contributors(request)) { it.getArtifacts(request) }
      .distinctItemsBy { it.result }

  override fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    parallelStream(contributors(request)) { it.getVersions(request) }
      .distinctItemsBy { it.result }

  private fun <C : DependencyCompletionContributor, R : BaseDependencyCompletionResult> parallelStream(
    contributors: List<C>,
    block: suspend (C) -> List<R>,
  ): Flow<DependencyCompletionEvent<R>> {
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
              block(contributor).forEach { send(DependencyCompletionEvent.Item(it)) }
            }
            catch (e: TimeoutCancellationException) {
              send(DependencyCompletionEvent.ServerTimedOut)
              throw e
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (t: Throwable) {
              send(DependencyCompletionEvent.ServerFailed(t))
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
              results.forEach { send(DependencyCompletionEvent.Item(it)) }
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

  private fun <C : DependencyCompletionContributor, R : BaseDependencyCompletionResult> simpleParallelStream(
    contributors: List<C>,
    block: suspend (C) -> List<R>,
  ): Flow<DependencyCompletionEvent<R>> = channelFlow {
    supervisorScope {
      contributors.forEach { contributor ->
        launch(Dispatchers.IO) {
          try {
            val results = block(contributor)
            for (r in results) {
              send(DependencyCompletionEvent.Item(r))
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

private fun <R : BaseDependencyCompletionResult, K> Flow<DependencyCompletionEvent<R>>.distinctItemsBy(
  keySelector: (R) -> K,
): Flow<DependencyCompletionEvent<R>> = flow {
  val seen = HashSet<K>()
  collect { event ->
    when (event) {
      is DependencyCompletionEvent.Item -> if (seen.add(keySelector(event.result))) emit(event)
      else -> emit(event)
    }
  }
}