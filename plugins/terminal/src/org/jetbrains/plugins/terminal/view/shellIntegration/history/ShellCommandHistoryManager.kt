// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.session.ShellName
import java.nio.file.Files
import java.nio.file.Path

/**
 * Owns the monolithic command history of a single shell session: commands from the shell history file,
 * followed by commands executed during the current session as they happen.
 */
internal class ShellCommandHistoryManager(private val coroutineScope: CoroutineScope) {
  private var historyLoadingJob: Job? = null
  private var commandHistory: ArrayDeque<String>? = null

  @RequiresEdt
  fun loadHistoryFile(historyPath: String?, eelDescriptor: EelDescriptor?, shellName: ShellName?) {
    if (commandHistory != null || historyLoadingJob != null) return

    if (historyPath == null || eelDescriptor == null || shellName == null) {
      LOG.warn("Failed to initialize shell history manager: required arguments are not initialized yet")
      commandHistory = ArrayDeque(HISTORY_COMMAND_LIMIT)
      return
    }

    val historyFile = try {
      EelPath.parse(historyPath, eelDescriptor).asNioPath()
    }
    catch (e: EelPathException) {
      LOG.warn("Cannot parse shell history path: $historyPath", e)
      null
    }

    historyLoadingJob = coroutineScope.launch(Dispatchers.Default) {
      val history = loadHistory(historyFile, shellName)
      withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
        if (commandHistory == null) {
          commandHistory = ArrayDeque<String>(HISTORY_COMMAND_LIMIT).apply {
            addAll(history)
          }
        }
      }
    }
  }

  private suspend fun loadHistory(historyFile: Path?, shellName: ShellName): List<String> {
    val file = historyFile ?: return emptyList()

    val content = try {
      withContext(Dispatchers.IO) {
        Files.readAllBytes(file)
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to read shell history from $file", e)
      return emptyList()
    }

    return try {
      createParser(shellName)?.parse(content, FILE_HISTORY_COMMAND_LIMIT).orEmpty()
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.error("Failed to parse shell history from $file. Content:\n${content.decodeToString()}", e)
      emptyList()
    }
  }

  @RequiresEdt
  fun addCommand(command: String) {
    val history = commandHistory ?: return
    val normalizedCommand = ShellHistoryParsingUtils.normalizeHistoryCommand(command) ?: return
    if (history.size == HISTORY_COMMAND_LIMIT) {
      history.removeFirst()
    }
    history.addLast(normalizedCommand)
  }

  @RequiresEdt
  fun getHistory(): List<String> = commandHistory.orEmpty()

  private fun createParser(shellName: ShellName): ShellCommandHistoryParser? {
    return when (shellName) {
      ShellName.BASH -> BashCommandHistoryParser()
      ShellName.ZSH -> ZshCommandHistoryParser()
      ShellName.POWERSHELL, ShellName.PWSH -> PowerShellCommandHistoryParser()
      else -> null
    }
  }

  private companion object {
    const val FILE_HISTORY_COMMAND_LIMIT: Int = 1_000
    const val HISTORY_COMMAND_LIMIT: Int = 1_500

    private val LOG = logger<ShellCommandHistoryManager>()
  }
}
