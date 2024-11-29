// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.classic

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.util.addModelListener
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class ClassicTerminalVfsRefreshService(private val coroutineScope: CoroutineScope) {
  fun create(widget: ShellTerminalWidget): ClassicTerminalVfsRefresher = ClassicTerminalVfsRefresher(widget, coroutineScope)
}

internal class ClassicTerminalVfsRefresher(private val widget: ShellTerminalWidget, private val coroutineScope: CoroutineScope) {
  private val currentWatcherRef: AtomicReference<CommandRunWatcher?> = AtomicReference()

  @Volatile
  private var isDisposed: Boolean = false

  init {
    Disposer.register(widget) {
      isDisposed = true
      stopWatcher()
    }
  }

  @RequiresEdt(generateAssertion = false)
  fun scheduleRefreshOnCommandFinished(isPromptSame: () -> Boolean) {
    if (!isDisposed && Registry.`is`("terminal.classic.refresh.vfs.on.shell.command.finished")) {
      stopWatcher()
      if (!widget.terminalTextBuffer.isUsingAlternateBuffer) {
        currentWatcherRef.set(CommandRunWatcher(isPromptSame))
      }
    }
  }

  private fun stopWatcher(watcher: CommandRunWatcher? = currentWatcherRef.get()) {
    if (watcher != null) {
      Disposer.dispose(watcher)
      // don't overwrite a new watcher concurrently set in `scheduleRefreshOnCommandFinished`
      currentWatcherRef.compareAndSet(watcher, null)
    }
  }

  private inner class CommandRunWatcher(private val isPromptSame: () -> Boolean): Disposable {
    private val changes: MutableSharedFlow<Unit> = MutableSharedFlow(0, 1, BufferOverflow.DROP_OLDEST)

    private val job: Job = coroutineScope.launch(Dispatchers.Default) {
      changes.collectLatest {
        delay(VFS_REFRESH_DELAY_MS)
        refreshVfsIfSamePromptIsShown()
      }
    }

    init {
      widget.terminalTextBuffer.addModelListener(this) {
        changes.tryEmit(Unit)
      }
      Disposer.register(widget, this)
    }

    private fun refreshVfsIfSamePromptIsShown() {
      if (isPromptSame()) {
        // Heuristic: the same prompt probably suggests that the last command has been finished.
        // Needs to be replaced with a "light" shell integration (for command finished events only).
        SaveAndSyncHandler.getInstance().scheduleRefresh()
        stopWatcher(this)
      }
    }

    override fun dispose() {
      job.cancel()
    }
  }
}

private val VFS_REFRESH_DELAY_MS: Duration = 500.milliseconds
