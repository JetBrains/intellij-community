// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import java.time.Duration

class IJShellRuntimeDataProvider(
  private val session: BlockTerminalSession,
  private val shellCommandExecutor: ShellCommandExecutor
) : ShellRuntimeDataProvider {
  @Volatile
  private var cachedShellEnv: ShellEnvironment? = null

  /** Path to the list of file names */
  private val filesCache: Cache<String, List<String>> = Caffeine.newBuilder()
    .maximumSize(5)
    .expireAfterAccess(Duration.ofMinutes(5))
    .scheduler(Scheduler.systemScheduler())
    .build()

  init {
    // Clear the cache if the user executed the command.
    // For example, the current directory can change and the file cache is no more valid.
    // Or some aliases added and the cached shell env is no more valid.
    session.addCommandListener(object : ShellCommandListener {
      override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
        clearCaches()
      }
    })

    // Clear the cache if project files are changed
    VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
      override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        clearCaches()
        return null
      }
    }, session)
  }

  override suspend fun getFilesFromDirectory(path: String): List<String> {
    var files = filesCache.getIfPresent(path)
    if (files.isNullOrEmpty()) {
      files = shellCommandExecutor.executeCommand(GetFilesCommand(path))
    }
    filesCache.put(path, files)
    return files
  }

  override suspend fun getShellEnvironment(): ShellEnvironment? {
    var env = cachedShellEnv
    if (env == null) {
      env = shellCommandExecutor.executeCommand(GetEnvironmentCommand(session))
    }
    cachedShellEnv = env
    return env
  }

  private fun clearCaches() {
    cachedShellEnv = null
    filesCache.invalidateAll()
  }

  companion object {
    val KEY: Key<ShellRuntimeDataProvider> = Key.create("ShellRuntimeDataProvider")
  }
}