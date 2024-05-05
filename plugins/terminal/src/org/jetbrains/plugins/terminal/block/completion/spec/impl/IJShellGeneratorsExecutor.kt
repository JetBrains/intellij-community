// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.terminal.block.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.CommandFinishedEvent
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import java.time.Duration

internal class IJShellGeneratorsExecutor(session: BlockTerminalSession) : ShellDataGeneratorsExecutor {
  private val cache: Cache<String, Any> = Caffeine.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun commandFinished(event: CommandFinishedEvent) {
        cache.invalidateAll()
      }
    })
  }

  override suspend fun <T> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T {
    return if (generator is ShellCacheableDataGenerator) {
      executeCacheableGenerator(context, generator)
    }
    else generator.generate(context)
  }

  private suspend fun <T> executeCacheableGenerator(context: ShellRuntimeContext, generator: ShellCacheableDataGenerator<T>): T {
    val key = generator.getCacheKey(context)
              ?: return generator.generate(context)
    val cachedResult = getCachedResult<T>(key)
    if (cachedResult != null) {
      return cachedResult
    }
    val result = generator.generate(context)
    cache.put(key, result)
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> getCachedResult(key: String): T? {
    val cachedResult = cache.getIfPresent(key) ?: return null
    return cachedResult as? T ?: run {
      LOG.error("Incorrect type of the cached generator result with key '$key', result: $cachedResult." +
                "There are two generators with the same cache keys and different return types.")
      null
    }
  }

  companion object {
    private val LOG: Logger = logger<IJShellGeneratorsExecutor>()

    val KEY: Key<IJShellGeneratorsExecutor> = Key.create("IJShellGeneratorsExecutor")
  }
}