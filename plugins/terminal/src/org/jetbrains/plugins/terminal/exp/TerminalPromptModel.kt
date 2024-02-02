// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList

class TerminalPromptModel(private val editor: EditorEx, session: BlockTerminalSession) {
  private val listeners: MutableList<TerminalPromptModelListener> = CopyOnWriteArrayList()
  private val renderer: TerminalPromptRenderer = BuiltInPromptRenderer(session)

  var renderingInfo: PromptRenderingInfo = PromptRenderingInfo("", emptyList())
    @RequiresEdt
    get
    private set

  val commandStartOffset: Int = 0

  val commandText: String
    get() = editor.document.getText(TextRange(commandStartOffset, editor.document.textLength))

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

  @RequiresEdt
  fun reset() {
    runWriteAction {
      editor.document.setText("")
    }
  }

  fun addListener(listener: TerminalPromptModelListener, disposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, disposable)
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  companion object {
    val KEY: Key<TerminalPromptModel> = Key.create("TerminalPromptModel")
  }
}

interface TerminalPromptModelListener {
  fun promptStateUpdated(renderingInfo: PromptRenderingInfo)
}

data class PromptRenderingInfo(val text: @NlsSafe String, val highlightings: List<HighlightingInfo>)

