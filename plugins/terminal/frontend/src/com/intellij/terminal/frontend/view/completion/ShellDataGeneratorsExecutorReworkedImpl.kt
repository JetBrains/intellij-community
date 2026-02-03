// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.doExecuteGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCacheableDataGenerator
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.time.Duration

/**
 * This service executes the provided [ShellRuntimeDataGenerator]'s.
 * If the generator is [ShellCacheableDataGenerator], the execution will be delegated to the [coroutineScope] and cached.
 * So the execution of such generators won't be canceled if [execute] is canceled.
 * And all further calls of [execute] with the same cache key will wait until the first one finishes.
 * This way, we will avoid abandoning the result of heavy generators.
 */
internal class ShellDataGeneratorsExecutorReworkedImpl(
  shellIntegration: TerminalShellIntegration,
  private val coroutineScope: CoroutineScope,
) : ShellDataGeneratorsExecutor {
  private val cache: Cache<String, Deferred<Any?>> = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  init {
    // Clear caches when the user executes the command
    shellIntegration.addCommandExecutionListener(coroutineScope.asDisposable(), object : TerminalCommandExecutionListener {
      override fun commandStarted(event: TerminalCommandStartedEvent) {
        reset()
      }
    })
  }

  override suspend fun <T : Any> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T? {
    return if (generator is ShellCacheableDataGenerator) {
      executeCacheableGenerator(context, generator)
    }
    else doExecuteGenerator(context, generator)
  }

  private suspend fun <T : Any> executeCacheableGenerator(context: ShellRuntimeContext, generator: ShellCacheableDataGenerator<T>): T? {
    val key = generator.getCacheKey(context)
    if (key == null) {
      return doExecuteGenerator(context, generator)
    }

    val deferred: Deferred<Any?> = cache.get(key) {
      coroutineScope.async {
        LOG.debug { "Executing cacheable generator with key '$key' in context: $context" }
        doExecuteGenerator(context, generator).also {
          LOG.debug { "Finished executing generator with key '$key'" }
        }
      }
    }

    val result: Any? = deferred.await()
    @Suppress("UNCHECKED_CAST")
    return result as? T
  }

  /** Clears the stored results and cancels all running generators */
  private fun reset() {
    cache.invalidateAll()
    val runningGeneratorsCount = coroutineScope.coroutineContext.job.children.count()
    coroutineScope.coroutineContext.cancelChildren()
    LOG.debug { "Cache was cleared and $runningGeneratorsCount running generators were canceled" }
  }

  companion object {
    private val LOG = logger<ShellDataGeneratorsExecutorReworkedImpl>()
  }
}