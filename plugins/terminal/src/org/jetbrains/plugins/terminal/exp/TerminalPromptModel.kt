// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.terminal.TerminalUtil
import java.awt.Font
import java.util.concurrent.CopyOnWriteArrayList

class TerminalPromptModel(session: BlockTerminalSession) : ShellCommandListener {
  private val listeners: MutableList<TerminalPromptStateListener> = CopyOnWriteArrayList()

  var renderingInfo: PromptRenderingInfo = PromptRenderingInfo("", emptyList())
    private set

  init {
    session.addCommandListener(this)
  }

  override fun promptStateUpdated(newState: TerminalPromptState) {
    runInEdt {
      renderingInfo = calculateRenderingInfo(newState)
      listeners.forEach { it.promptStateUpdated(renderingInfo) }
    }
  }

  private fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo {
    val directory = calculateDirectoryText(state.currentDirectory)
    val directoryAttributes = TextAttributes(TerminalUi.promptForeground, null, null, null, Font.PLAIN)
    val directoryHighlighting = HighlightingInfo(0, directory.length, directoryAttributes)
    return PromptRenderingInfo(directory, listOf(directoryHighlighting))
  }

  private fun calculateDirectoryText(directory: String): @NlsSafe String {
    return if (directory != SystemProperties.getUserHome()) {
      FileUtil.getLocationRelativeToUserHome(directory, false)
    }
    else "~"
  }

  fun addListener(listener: TerminalPromptStateListener, disposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, disposable)
  }
}

interface TerminalPromptStateListener {
  fun promptStateUpdated(renderingInfo: PromptRenderingInfo)
}

data class PromptRenderingInfo(val text: @NlsSafe String, val highlightings: List<HighlightingInfo>)

