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
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.util.fireListenersAndLogAllExceptions
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet

internal class TerminalTextSelectionModelImpl(
  private val outputModels: TerminalOutputModelsSet,
  private val regularEditor: Editor,
  private val alternateBufferEditor: Deferred<Editor>,
  coroutineScope: CoroutineScope,
) : TerminalTextSelectionModel {
  override val selection: TerminalTextSelection?
    get() = getCurrentSelection()

  private val listeners = DisposableWrapperList<TerminalTextSelectionListener>()

  init {
    val listener = MyEditorSelectionListener()
    val parentDisposable = coroutineScope.asDisposable()
    regularEditor.selectionModel.addSelectionListener(listener, parentDisposable)
    // The handler runs synchronously on completion, so the listener gets every selection change of the new editor.
    alternateBufferEditor.invokeOnCompletion {
      alternateBufferEditor.getNow()?.selectionModel?.addSelectionListener(listener, parentDisposable)
    }
  }

  override fun updateSelection(newSelection: TerminalTextSelection?) {
    val outputModel = outputModels.active.value
    val editorSelectionModel = getEditor(outputModel).selectionModel
    if (newSelection != null) {
      if (newSelection.startOffset !in outputModel.startOffset..outputModel.endOffset ||
          newSelection.endOffset !in outputModel.startOffset..outputModel.endOffset) {
        error("Selection range is out of model bounds: $newSelection, model: $outputModel")
      }

      val start = newSelection.startOffset - outputModel.startOffset
      val end = newSelection.endOffset - outputModel.startOffset
      editorSelectionModel.setSelection(start.toInt(), end.toInt())
    }
    else editorSelectionModel.removeSelection(true)
  }

  private fun getCurrentSelection(): TerminalTextSelection? {
    val outputModel = outputModels.active.value
    val editorSelectionModel = getEditor(outputModel).selectionModel
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

  /** The view makes the alternate model active only after it creates the alternate buffer editor. */
  private fun getEditor(outputModel: TerminalOutputModel): Editor {
    return if (outputModel == outputModels.regular) {
      regularEditor
    }
    else checkNotNull(alternateBufferEditor.getNow()) { "The alternate buffer editor is not created yet" }
  }

  private inner class MyEditorSelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      val outputModel = when (e.editor) {
        regularEditor -> outputModels.regular
        alternateBufferEditor.getNow() -> outputModels.alternative
        else -> error("Unexpected editor: ${e.editor}")
      }

      val oldSelection = e.oldRange.toTerminalTextSelection(outputModel)
      val newSelection = e.newRange.toTerminalTextSelection(outputModel)

      if (newSelection != oldSelection) {
        val event = TerminalTextSelectionChangeEventImpl(outputModel, oldSelection, newSelection)
        fireListenersAndLogAllExceptions(listeners, LOG, { "Exception during handling $event" }) {
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