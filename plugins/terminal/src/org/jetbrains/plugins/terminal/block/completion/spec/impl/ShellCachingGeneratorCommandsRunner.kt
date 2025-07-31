// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import org.jetbrains.annotations.ApiStatus
import java.time.Duration

@ApiStatus.Internal
class ShellCachingGeneratorCommandsRunner(private val delegate: ShellCommandExecutor) : ShellCommandExecutor {
  val cache: Cache<String, ShellCommandResult> = Caffeine.newBuilder()
    .maximumSize(5)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  override suspend fun runShellCommand(command: String): ShellCommandResult {
    cache.getIfPresent(command)?.let { return it }
    val result = delegate.runShellCommand(command)
    if (result.exitCode == 0) {
      cache.put(command, result)
    }
    return result
  }

  fun reset() {
    cache.invalidateAll()
  }

}
