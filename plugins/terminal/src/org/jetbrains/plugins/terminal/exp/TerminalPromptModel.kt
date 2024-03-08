// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt

class TerminalPromptModel(private val editor: EditorEx, session: BlockTerminalSession) {
  private val renderer: TerminalPromptRenderer = BuiltInPromptRenderer(session)
  private val document: DocumentEx
    get() = editor.document

  var promptRenderingInfo: PromptRenderingInfo = PromptRenderingInfo("", emptyList())
    @RequiresEdt
    get
    private set

  val commandStartOffset: Int
    get() = promptRenderingInfo.text.length

  var commandText: String
    get() = document.getText(TextRange(commandStartOffset, document.textLength))
    set(value) {
      DocumentUtil.writeInRunUndoTransparentAction {
        document.replaceString(commandStartOffset, document.textLength, value)
      }
    }

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        val updatedInfo = renderer.calculateRenderingInfo(newState)
        runInEdt {
          updatePrompt(updatedInfo)
          promptRenderingInfo = updatedInfo
        }
      }
    })

    // Used in TerminalPromptFileViewProvider
    editor.virtualFile.putUserData(KEY, this)
    editor.virtualFile.putUserData(BlockTerminalSession.KEY, session)

    editor.caretModel.addCaretListener(PreventMoveToPromptListener())
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(document) { /* do nothing */ }

    editor.project!!.messageBus.connect(session).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      updatePrompt(promptRenderingInfo)
    })
  }

  @RequiresEdt
  fun reset() {
    commandText = ""
    editor.caretModel.moveToOffset(document.textLength)
  }

  private fun updatePrompt(renderingInfo: PromptRenderingInfo) {
    DocumentUtil.writeInRunUndoTransparentAction {
      document.guardedBlocks.clear()
      document.replaceString(0, commandStartOffset, renderingInfo.text)
      document.createGuardedBlock(0, renderingInfo.text.length)
    }
    editor.markupModel.removeAllHighlighters()
    for (highlighting in renderingInfo.highlightings) {
      editor.markupModel.addRangeHighlighter(highlighting.startOffset, highlighting.endOffset, HighlighterLayer.SYNTAX,
                                             highlighting.textAttributesProvider.getTextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    }
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      document.addDocumentListener(listener, disposable)
    }
    else document.addDocumentListener(listener)
  }

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

  companion object {
    val KEY: Key<TerminalPromptModel> = Key.create("TerminalPromptModel")
  }
}

data class PromptRenderingInfo(val text: @NlsSafe String, val highlightings: List<HighlightingInfo>)

