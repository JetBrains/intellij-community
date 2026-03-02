// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.ActionLink
import com.jetbrains.python.inspections.interpreter.InterpreterFix
import com.jetbrains.python.inspections.interpreter.BusyGuardExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * This class is intended for async computation of interpreter fixes.
 * It should have the same lifecycle as the notification provider.
 */
@ApiStatus.Internal
class PyAsyncFileInspectionRunner(
  @NlsContexts.ProgressTitle private val progressTitle: String,
  cacheTtl: Duration = 20.seconds,
  private val cacheLoader: suspend (Module) -> InspectionRunnerResult,
) {
  private val cache: LoadingCache<Module, Deferred<InspectionRunnerResult>> = Caffeine.newBuilder()
    .refreshAfterWrite(cacheTtl.toJavaDuration())
    .weakKeys()
    .build(object : CacheLoader<Module, Deferred<InspectionRunnerResult>> {
      override fun load(key: Module): Deferred<InspectionRunnerResult> = startComputation(key)

      /**
       * On refresh, the old (completed) [Deferred] is served to callers while the new one is loading.
       * The cache entry is replaced only when the new [Deferred] completes.
       * This prevents the notification from flickering (disappearing and reappearing on cache refresh).
       */
      override fun asyncReload(
        key: Module,
        oldValue: Deferred<InspectionRunnerResult>,
        executor: Executor,
      ): CompletableFuture<out Deferred<InspectionRunnerResult>> {
        val deferred = startComputation(key)
        return deferred.asCompletableFuture().thenApply { deferred }
      }
    })

  private fun startComputation(module: Module): Deferred<InspectionRunnerResult> {
    val project = module.project
    val deferred = project.service<InterpreterFixExecutor>().scope.async {
      withBackgroundProgress(project, progressTitle) {
        cacheLoader(module)
      }
    }
    deferred.invokeOnCompletion { updateNotifications(module) }
    return deferred
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun runInspection(module: Module): List<InterpreterFix>? {
    val inspectionResult = cache.get(module).takeIf { it.isCompleted } ?: return null

    if (inspectionResult.isCancelled) {
      cache.invalidate(module)
      return null
    }

    val (fixes, shouldCache) = inspectionResult.getCompleted()
    if (!shouldCache) {
      cache.invalidate(module)
    }
    return fixes.map { CacheEvictingFix(it) { cache.invalidate(module) } }
  }

  private fun updateNotifications(module: Module) {
    val project = module.project
    // Must use a separate coroutine scope: invokeOnCompletion runs inline in the completing
    // coroutine's context, and EditorNotifications.getInstance() may need runBlocking for
    // service initialization, which fails inside an already-completed coroutine scope.
    project.serviceIfCreated<InterpreterFixExecutor>()?.scope?.launch {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}

@ApiStatus.Internal
data class InspectionRunnerResult(
  val fixes: List<InterpreterFix>,
  val shouldCache: Boolean,
)

private class CacheEvictingFix(
  private val fix: InterpreterFix,
  private val cacheEvictor: () -> Unit,
) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): ActionLink {
    val link = fix.createActionLink(module, project, psiFile, executor)
    link.addActionListener { cacheEvictor() }
    return link
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InterpreterFixExecutor(private val project: Project, internal val scope: CoroutineScope) : BusyGuardExecutor {
  private val semaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_LATEST)
  private val _isBusy = MutableStateFlow(false)
  override val isBusy: Boolean get() = _isBusy.value

  init {
    scope.launch {
      _isBusy.collect { EditorNotifications.getInstance(project).updateAllNotifications() }
    }
  }

  override fun execute(action: suspend () -> Unit) {
    scope.launch {
      semaphore.withPermit {
        _isBusy.value = true
        try {
          action()
        }
        finally {
          _isBusy.value = false
        }
      }
    }
  }
}
