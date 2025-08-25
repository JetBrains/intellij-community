// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.frontend.TerminalSessionController
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCacheableDataGenerator
import org.jetbrains.plugins.terminal.block.reworked.TerminalShellIntegrationEventsListener
import java.time.Duration

/**
 * This service executes the provided [ShellRuntimeDataGenerator]'s.
 * If the generator is [ShellCacheableDataGenerator], the execution will be delegated to the [coroutineScope] and cached.
 * So the execution of such generators won't be canceled if [execute] is canceled.
 * And all further calls of [execute] with the same cache key will wait until the first one finishes.
 * This way, we will avoid abandoning the result of heavy generators.
 */
internal class ShellDataGeneratorsExecutorReworkedImpl(
  sessionController: TerminalSessionController,
  private val coroutineScope: CoroutineScope,
) : ShellDataGeneratorsExecutor {
  private val cache: Cache<String, Deferred<Any>> = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  init {
    // Clear caches when the user executes the command
    sessionController.addShellIntegrationListener(coroutineScope.asDisposable(), object : TerminalShellIntegrationEventsListener {
      override fun commandStarted(command: String) {
        reset()
      }
    })
  }

  override suspend fun <T : Any> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T {
    return if (generator is ShellCacheableDataGenerator) {
      executeCacheableGenerator(context, generator)
    }
    else generator.generate(context)
  }

  private suspend fun <T : Any> executeCacheableGenerator(context: ShellRuntimeContext, generator: ShellCacheableDataGenerator<T>): T {
    val key = generator.getCacheKey(context)
    if (key == null) {
      return generator.generate(context)
    }

    val deferred: Deferred<Any> = cache.get(key) {
      coroutineScope.async {
        generator.generate(context)
      }
    }
    val result: Any = deferred.await()

    @Suppress("UNCHECKED_CAST")
    return result as? T
           ?: error("Incorrect type of the cached generator result with key '$key', result: '$result'\n" +
                    "There are two generators with the same cache keys and different return types.")
  }

  /** Clears the stored results and cancels all running generators */
  private fun reset() {
    cache.invalidateAll()
    coroutineScope.coroutineContext.cancelChildren()
  }
}