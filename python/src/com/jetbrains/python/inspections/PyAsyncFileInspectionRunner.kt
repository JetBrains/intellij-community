// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.FileContentUtilCore
import com.jetbrains.python.psi.PyFile
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * This class is intended for async file inspections. It should have the same lifecycle as the inspection, the best
 * way is to create the instance of this runner as a property of the inspection itself.
 */
internal class PyAsyncFileInspectionRunner(
  @NlsContexts.ProgressTitle private val progressTitle: String,
  cacheTtl: Duration = 20.seconds,
  cacheLoader: suspend (Module) -> List<LocalQuickFix>,
) {
  private val cache: LoadingCache<Module, Deferred<List<LocalQuickFix>>> = Caffeine.newBuilder()
    .expireAfterWrite(cacheTtl.toJavaDuration())
    .weakKeys()
    .build { module ->
      val project = module.project
      project.service<InspectionRunnerService>().scope.async {
        withBackgroundProgress(module.project, progressTitle) {
          cacheLoader(module)
        }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun runInspection(node: PyFile, module: Module): List<LocalQuickFix>? {
    val cached = cache.getIfPresent(module) != null
    val fixes = cache.get(module)
    if (fixes.isCompleted) {
      return fixes.getCompleted()
    }

    if (!cached) {
      fixes.invokeOnCompletion {
        val project = module.project
        project.serviceIfCreated<InspectionRunnerService>()?.scope?.launch {
          backgroundWriteAction {
            DaemonCodeAnalyzer.getInstance(project).restart(node, "$progressTitle finished")
          }
        } ?: thisLogger().warn("No service was found, this is most likely due to project being disposed")
      }
    }

    return null
  }
}

@Service(Service.Level.PROJECT)
private class InspectionRunnerService(val scope: CoroutineScope)
