// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList

class TerminalPromptModel(private val session: BlockTerminalSession) {
  private val listeners: MutableList<TerminalPromptStateListener> = CopyOnWriteArrayList()
  private val renderer: TerminalPromptRenderer = BuiltInPromptRenderer(session)

  var renderingInfo: PromptRenderingInfo = PromptRenderingInfo("", emptyList())
    @RequiresEdt
    get
    private set

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        val updatedInfo = renderer.calculateRenderingInfo(newState)
        runInEdt {
          renderingInfo = updatedInfo
          listeners.forEach { it.promptStateUpdated(updatedInfo) }
        }
      }
    })
  }

  fun addListener(listener: TerminalPromptStateListener, disposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, disposable)
  }
}

interface TerminalPromptStateListener {
  fun promptStateUpdated(renderingInfo: PromptRenderingInfo)
}

data class PromptRenderingInfo(val text: @NlsSafe String, val highlightings: List<HighlightingInfo>)

