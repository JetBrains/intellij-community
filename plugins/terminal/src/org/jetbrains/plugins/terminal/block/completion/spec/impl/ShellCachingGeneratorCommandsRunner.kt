// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.terminal.completion.spec.ShellCommandResult
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.CommandFinishedEvent
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import java.time.Duration

internal class ShellCachingGeneratorCommandsRunner(private val session: BlockTerminalSession) : ShellGeneratorCommandsRunner {
  val cache: Cache<String, ShellCommandResult> = Caffeine.newBuilder()
    .maximumSize(5)
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

  override suspend fun runGeneratorCommand(command: String): ShellCommandResult {
    cache.getIfPresent(command)?.let { return it }
    val result = session.commandExecutionManager.runGeneratorAsync(command).await()
    if (result.exitCode == 0) {
      cache.put(command, result)
    }
    return result
  }
}
