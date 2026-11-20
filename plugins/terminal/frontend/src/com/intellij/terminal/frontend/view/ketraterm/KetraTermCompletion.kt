// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(
  io.github.ketraterm.completion.api.ExperimentalTerminalCompletionApi::class,
  io.github.ketraterm.ui.swing.suggestion.ExperimentalShellSuggestionsApi::class,
)

package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.asDisposable
import io.github.ketraterm.completion.api.TerminalCompletionEngines
import io.github.ketraterm.completion.api.TerminalCompletionSource
import io.github.ketraterm.completion.api.TerminalCompletionSourceEntry
import io.github.ketraterm.completion.api.TerminalCompletionSourcePrior
import io.github.ketraterm.completion.api.TerminalCompletionSources
import io.github.ketraterm.completion.host.TerminalLocalFileSystemProvider
import io.github.ketraterm.completion.host.TerminalCompletionSourceEnvironment
import io.github.ketraterm.completion.api.TerminalShellCapabilities
import io.github.ketraterm.ui.swing.host.SwingCompletionContext
import io.github.ketraterm.completion.session.SessionShellCompletionProvider
import io.github.ketraterm.ui.swing.host.SwingCompletionSuggestionProvider
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestion
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionProvider
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionRequest
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionSource
import io.github.ketraterm.ui.swing.suggestion.SwingShellSuggestionTrigger
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.session.ShellName

internal class KetraTermCompletion(
  private val project: Project,
  private val view: KetraTermTerminalView,
  private val session: KetraTermTerminalSession,
  parentScope: CoroutineScope,
) : SwingShellSuggestionProvider, AutoCloseable {
  private val scope = parentScope.childScope("KetraTerm completion")
  private val shellName = session.startupOptions.takeIf { it.shellCommand.isNotEmpty() }?.guessShellName()
  @Volatile private var closed = false
  private var shortcuts = JBTerminalSystemSettingsProviderBase.getKeyStrokesByActionId("Terminal.CommandCompletion.Invoke")
  private val pathSource = TerminalCompletionSources.path(TerminalLocalFileSystemProvider())
  private val provider = SwingCompletionSuggestionProvider(TerminalCompletionEngines.fromSources(
    sources = if (session.eelDescriptor == LocalEelDescriptor) listOf(
      TerminalCompletionSourceEntry(
        TerminalCompletionSource { request, context, limit ->
          val resolved = TerminalCompletionSourceEnvironment.resolve(request, context)
                         ?: return@TerminalCompletionSource emptyList()
          pathSource.complete(resolved, context, limit)
        },
        TerminalCompletionSourcePrior.DIRECTORY_PATH,
      ),
    ) else emptyList(),
    shellProvider = SessionShellCompletionProvider(session.terminalSession),
  ), {
    SwingCompletionContext(
      profileId = shellName?.value,
      workingDirectoryUri = session.shellState.currentWorkingDirectoryUri(),
      shellCapabilities = when (shellName) {
        ShellName.POWERSHELL, ShellName.PWSH -> TerminalShellCapabilities.POWERSHELL
        ShellName.BASH, ShellName.ZSH -> TerminalShellCapabilities.POSIX
        else -> TerminalShellCapabilities.PLAIN
      },
    )
  })

  init {
    ApplicationManager.getApplication().messageBus.connect(scope.asDisposable()).subscribe(
      KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) { refreshSettings() }
        override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) {
          if ("Terminal.CommandCompletion.Invoke" in actionIds) refreshSettings()
        }
      },
    )
    view.terminal.setSharedShellSuggestionProvider(this)
  }

  fun handleKeyPressed(event: KeyEvent): Boolean =
    event.id == KeyEvent.KEY_PRESSED &&
    shortcuts.any { it.keyCode == event.keyCode && it == KeyStroke.getKeyStrokeForEvent(event) } && invokeCompletion()

  fun isAvailable(): Boolean = !closed && session.terminalSession.activeShellCommandLine() != null &&
                               TerminalCommandCompletion.isEnabled(project)

  fun invokeCompletion(): Boolean {
    if (!isAvailable()) return false
    view.terminal.requestActiveShellSuggestions(SwingShellSuggestionTrigger.EXPLICIT)
    return true
  }

  override fun open(request: SwingShellSuggestionRequest): SwingShellSuggestionSource =
    if (isAvailable()) provider.open(request) else SwingShellSuggestionSource(emptyFlow())

  override fun suggestions(request: SwingShellSuggestionRequest): Flow<List<SwingShellSuggestion>> =
    if (isAvailable()) provider.suggestions(request) else emptyFlow()

  fun invalidateInput() {
    view.terminal.hideShellSuggestions()
  }
  fun refreshSettings() {
    shortcuts = JBTerminalSystemSettingsProviderBase.getKeyStrokesByActionId("Terminal.CommandCompletion.Invoke")
    view.terminal.refreshShellSuggestions()
  }

  override fun close() {
    if (closed) return
    closed = true
    view.terminal.setSharedShellSuggestionProvider(null)
    scope.cancel()
  }
}
