package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.frontend.view.TerminalTextSelection
import com.intellij.terminal.frontend.view.TerminalTextSelectionChangeEvent
import com.intellij.terminal.frontend.view.TerminalTextSelectionListener
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.util.asDisposable
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet

internal class TerminalTextSelectionModelImpl(
  private val outputModels: TerminalOutputModelsSet,
  private val regularEditor: Editor,
  private val alternateBufferEditor: Editor,
  coroutineScope: CoroutineScope,
) : TerminalTextSelectionModel {
  override val selection: TerminalTextSelection?
    get() = getCurrentSelection()

  private val listeners = DisposableWrapperList<TerminalTextSelectionListener>()

  init {
    val listener = MyEditorSelectionListener()
    regularEditor.selectionModel.addSelectionListener(listener, coroutineScope.asDisposable())
    alternateBufferEditor.selectionModel.addSelectionListener(listener, coroutineScope.asDisposable())
  }

  override fun updateSelection(newSelection: TerminalTextSelection?) {
    val outputModel = outputModels.active.value
    val curEditor = if (outputModel == outputModels.regular) regularEditor else alternateBufferEditor
    val editorSelectionModel = curEditor.selectionModel
    if (newSelection != null) {
      if (newSelection.startOffset !in outputModel.startOffset..outputModel.endOffset ||
          newSelection.endOffset !in outputModel.startOffset..outputModel.endOffset) {
        error("Selection range is out of model bounds: $newSelection, model: $outputModel")
      }

      val start = newSelection.startOffset - outputModel.startOffset
      val end = newSelection.endOffset - outputModel.startOffset
      editorSelectionModel.setSelection(start.toInt(), end.toInt())
    }
    else editorSelectionModel.removeSelection()
  }

  private fun getCurrentSelection(): TerminalTextSelection? {
    val outputModel = outputModels.active.value
    val curEditor = if (outputModel == outputModels.regular) regularEditor else alternateBufferEditor
    val editorSelectionModel = curEditor.selectionModel
    return if (editorSelectionModel.hasSelection()) {
      TerminalTextSelection.of(
        outputModel.startOffset + editorSelectionModel.selectionStart.toLong(),
        outputModel.startOffset + editorSelectionModel.selectionEnd.toLong(),
      )
    }
    else null
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalTextSelectionListener) {
    listeners.add(listener, parentDisposable)
  }

  private inner class MyEditorSelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      val outputModel = when (e.editor) {
        regularEditor -> outputModels.regular
        alternateBufferEditor -> outputModels.alternative
        else -> error("Unexpected editor: ${e.editor}")
      }

      val oldSelection = e.oldRange.toTerminalTextSelection(outputModel)
      val newSelection = e.newRange.toTerminalTextSelection(outputModel)

      if (newSelection != oldSelection) {
        val event = TerminalTextSelectionChangeEventImpl(outputModel, oldSelection, newSelection)
        fireListenersAndLogAllExceptions(listeners, LOG, "Exception during handling $event") {
          it.selectionChanged(event)
        }
      }
    }

    private fun TextRange.toTerminalTextSelection(model: TerminalOutputModel): TerminalTextSelection? {
      return if (!isEmpty) {
        TerminalTextSelection.of(
          model.startOffset + startOffset.toLong(),
          model.startOffset + endOffset.toLong(),
        )
      }
      else null
    }
  }

  private data class TerminalTextSelectionChangeEventImpl(
    override val outputModel: TerminalOutputModel,
    override val oldSelection: TerminalTextSelection?,
    override val newSelection: TerminalTextSelection?,
  ) : TerminalTextSelectionChangeEvent

  companion object {
    private val LOG = logger<TerminalTextSelectionModelImpl>()
  }
}