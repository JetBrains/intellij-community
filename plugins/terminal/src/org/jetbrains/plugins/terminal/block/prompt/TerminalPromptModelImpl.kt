// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorDescription
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorStateListener
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener
import java.util.concurrent.CopyOnWriteArrayList

internal class TerminalPromptModelImpl(
  override val editor: EditorEx,
  private val session: BlockTerminalSession,
) : TerminalPromptModel, Disposable {
  private val document: DocumentEx
    get() = editor.document

  private var renderer: TerminalPromptRenderer = createPromptRenderer()

  private var rightPromptManager: RightPromptManager? = null

  override var promptState: TerminalPromptState = TerminalPromptState(currentDirectory = "")
    private set

  override var renderingInfo: TerminalPromptRenderingInfo = TerminalPromptRenderingInfo("", emptyList())
    private set

  override val commandStartOffset: Int
    get() = renderingInfo.text.length

  override var commandText: String
    get() = document.getText(TextRange(commandStartOffset, document.textLength))
    set(value) {
      DocumentUtil.writeInRunUndoTransparentAction {
        document.replaceString(commandStartOffset, document.textLength, value)
      }
    }

  private val errorStateListeners: MutableList<TerminalPromptErrorStateListener> = CopyOnWriteArrayList()

  init {
    editor.caretModel.addCaretListener(PreventMoveToPromptListener(), this)
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(document) { /* do nothing */ }

    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        updatePrompt(newState)
      }
    })

    editor.project!!.messageBus.connect(this).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      doUpdatePrompt(renderingInfo)
    })
    BlockTerminalOptions.getInstance().addListener(this) {
      renderer = createPromptRenderer()
      updatePrompt(promptState)
    }
  }

  @RequiresEdt
  override fun resetChangesHistory() {
    val undoManager = UndoManager.getInstance(editor.project!!) as UndoManagerImpl
    undoManager.invalidateActionsFor(DocumentReferenceManager.getInstance().create(document))
  }

  private fun updatePrompt(state: TerminalPromptState) {
    val updatedInfo = renderer.calculateRenderingInfo(state)
    runInEdt {
      doUpdatePrompt(updatedInfo)
      promptState = state
      renderingInfo = updatedInfo
    }
  }

  @RequiresEdt
  private fun doUpdatePrompt(renderingInfo: TerminalPromptRenderingInfo) {
    DocumentUtil.writeInRunUndoTransparentAction {
      document.guardedBlocks.forEach { document.removeGuardedBlock(it) }
      document.replaceString(0, commandStartOffset, renderingInfo.text)
      document.createGuardedBlock(0, renderingInfo.text.length)
    }
    editor.markupModel.removeAllHighlighters()
    for (highlighting in renderingInfo.highlightings) {
      editor.markupModel.addRangeHighlighter(highlighting.startOffset, highlighting.endOffset, HighlighterLayer.SYNTAX,
                                             highlighting.textAttributesProvider.getTextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    }

    val rightPrompt = renderingInfo.rightText
    if (rightPrompt.isNotEmpty()) {
      val manager = getOrCreateRightPromptManager()
      manager.update(renderingInfo.text.length, rightPrompt, renderingInfo.rightHighlightings)
    }
    else {
      rightPromptManager?.let { Disposer.dispose(it) }
      rightPromptManager = null
    }
  }

  private fun getOrCreateRightPromptManager(): RightPromptManager {
    rightPromptManager?.let { return it }
    val manager = RightPromptManager(editor, session.settings)
    Disposer.register(this, manager)
    rightPromptManager = manager
    return manager
  }

  private fun createPromptRenderer(): TerminalPromptRenderer {
    return when (BlockTerminalOptions.getInstance().promptStyle) {
      TerminalPromptStyle.SINGLE_LINE -> BuiltInPromptRenderer(session.colorPalette, isSingleLine = true)
      TerminalPromptStyle.DOUBLE_LINE -> BuiltInPromptRenderer(session.colorPalette, isSingleLine = false)
      TerminalPromptStyle.SHELL -> {
        val sizeProvider = { session.model.withContentLock { TermSize(session.model.width, session.model.height) } }
        ShellPromptRenderer(session.colorPalette, session.settings, sizeProvider)
      }
    }
  }

  override fun setErrorDescription(errorDescription: TerminalPromptErrorDescription?) {
    for (listener in errorStateListeners) {
      listener.errorStateChanged(errorDescription)
    }
  }

  override fun addErrorStateListener(listener: TerminalPromptErrorStateListener, parentDisposable: Disposable) {
    TerminalUtil.addItem(errorStateListeners, listener, parentDisposable)
  }

  override fun dispose() {}

  /**
   * Listener that prevents the caret from moving to a position inside the prompt.
   * Instead, it moves the caret to the start of the command.
   */
  private inner class PreventMoveToPromptListener : CaretListener {
    private var preventRecursion = false

    override fun caretPositionChanged(event: CaretEvent) {
      if (preventRecursion) return
      val newOffset = editor.logicalPositionToOffset(event.newPosition)
      if (newOffset < commandStartOffset) {
        preventRecursion = true
        try {
          editor.caretModel.moveToOffset(commandStartOffset)
        }
        finally {
          preventRecursion = false
        }
      }
    }
  }
}