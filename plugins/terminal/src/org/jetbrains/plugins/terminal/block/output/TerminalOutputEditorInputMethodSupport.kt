// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.output

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorInputMethodSupport
import com.intellij.openapi.editor.impl.InputMethodInlayRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.text.CharacterIterator
import javax.swing.SwingUtilities

@ApiStatus.Internal
class TerminalOutputEditorInputMethodSupport(
  private val editor: EditorEx,
  private val coroutineScope: CoroutineScope,
  private val getCaretPosition: () -> LogicalPosition?,
  private val cursorOffsetFlow: StateFlow<Int>?,
  private val sendInputString: (String) -> Unit,
) {

  private val inputMethodRequests = MyInputMethodRequests()

  // should be accessed in EDT only
  private var composedTextInlayTracker: ComposedTextInlayTracker? = null

  init {
    check(editor.isViewer)

    val mouseListener = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        if (composedTextInlayTracker != null && !editor.isDisposed) {
          editor.contentComponent.getInputContext()?.endComposition()
          composedTextInlayTracker?.cancel()
          composedTextInlayTracker = null
        }
      }
    }
    editor.contentComponent.addMouseListener(mouseListener)

    val inputMethodListener = object : InputMethodListener {
      override fun inputMethodTextChanged(event: InputMethodEvent) {
        if (!editor.isDisposed) {
          handleInputMethodTextChanged(event)
        }
        event.consume()
      }

      override fun caretPositionChanged(event: InputMethodEvent) {
        event.consume()
      }
    }

    (editor as EditorImpl).setInputMethodSupport(EditorInputMethodSupport(inputMethodRequests, inputMethodListener))

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      runInEdt(ModalityState.any()) {
        editor.contentComponent.removeMouseListener(mouseListener)
        editor.setInputMethodSupport(null)
      }
    }
  }

  private fun handleInputMethodTextChanged(event: InputMethodEvent) {
    composedTextInlayTracker?.cancel()
    composedTextInlayTracker = null

    val text: AttributedCharacterIterator? = event.text
    if (text != null) {
      text.first() // set iterator to the text beginning
      val committedString = collectString(text, event.committedCharacterCount)
      if (committedString.isNotEmpty()) {
        sendInputString(committedString)
      }
      val composedString = collectString(text)
      if (composedString.isNotEmpty()) {
        composedTextInlayTracker = ComposedTextInlayTracker(composedString).also {
          it.start()
        }
      }
    }
  }

  private fun collectString(text: AttributedCharacterIterator, count: Int = Integer.MAX_VALUE): String {
    var processedChars = 0
    val result = StringBuilder()
    var c: Char = text.current()
    while (c != CharacterIterator.DONE && processedChars < count) {
      // Hack just like in com.intellij.openapi.editor.impl.EditorImpl.MyInputMethodHandler.replaceInputMethodText
      if (c.code >= 0x20 && c.code != 0x7F) {
        result.append(c)
      }
      c = text.next()
      processedChars++
    }
    return result.toString()
  }

  private inner class MyInputMethodRequests : InputMethodRequests {

    override fun getTextLocation(offset: TextHitInfo?): Rectangle {
      if (editor.isDisposed()) return Rectangle()
      val cursorPosition = getCaretPosition() ?: return Rectangle()
      val caret: Point = editor.logicalPositionToXY(cursorPosition)
      val r = Rectangle(caret, Dimension(1, editor.getLineHeight()))
      val p = getLocationOnScreen(editor.getContentComponent())
      r.translate(p.x, p.y)
      return r
    }

    override fun getLocationOffset(x: Int, y: Int): TextHitInfo? = null

    override fun getInsertPositionOffset(): Int {
      val cursorLogicalPosition = getCaretPosition() ?: return 0
      return editor.logicalPositionToOffset(cursorLogicalPosition)
    }

    override fun getCommittedText(beginIndex: Int, endIndex: Int, attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator {
      val committed = editor.getText(beginIndex, endIndex)
      return AttributedString(committed).iterator
    }

    override fun getCommittedTextLength(): Int = editor.getDocument().textLength

    override fun cancelLatestCommittedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? =
      null

    override fun getSelectedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? = null
  }

  private inner class ComposedTextInlayTracker(private val composedText: String) {
    private var inlay: Inlay<*>? = null
    private var cursorOffset: Int = -1
    private var job: Job? = null

    @RequiresEdt
    fun start() {
      val initialCaretPosition = getCaretPosition() ?: return
      showInlayAt(editor.logicalPositionToOffset(initialCaretPosition))
      if (cursorOffsetFlow != null) {
        job = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          cursorOffsetFlow.collect { newCursorOffset ->
            if (newCursorOffset != cursorOffset) {
              showInlayAt(newCursorOffset)
            }
          }
        }
      }
    }

    private fun showInlayAt(newCursorOffset: Int) {
      hideInlay()
      inlay = editor.getInlayModel().addInlineElement<InputMethodInlayRenderer>(newCursorOffset, true, -IME_INLAY_PRIORITY,
                                                                                InputMethodInlayRenderer(composedText))
      LOG.debug {
        val msg = if (cursorOffset == -1) {
          "Initialized composed text inlay, offset: $newCursorOffset, composed text: $composedText"
        }
        else {
          "Repositioned composed text inlay, new offset: $newCursorOffset"
        }
        if (inlay != null) msg else "$msg (INLAY NOT SHOWN)"
      }
      cursorOffset = newCursorOffset
    }

    private fun hideInlay() {
      inlay?.let {
        Disposer.dispose(it)
      }
      inlay = null
    }

    @RequiresEdt
    fun cancel() {
      job?.cancel()
      hideInlay()
    }
  }
}

private val LOG: Logger = logger<TerminalOutputEditorInputMethodSupport>()

private fun getLocationOnScreen(component: Component): Point {
  return Point().also {
    SwingUtilities.convertPointToScreen(it, component)
  }
}

private fun Editor.getText(startIdx: Int, endIdx: Int): String {
  if (startIdx in 0..<endIdx) {
    return getDocument().getImmutableCharSequence().subSequence(startIdx, endIdx).toString()
  }
  return ""
}

/**
 * Very high inlay priority to keep IME inlays to be always the nearest to the caret.
 * Not the Integer.MAX_VALUE to prevent accidental overflow.
 */
private const val IME_INLAY_PRIORITY = 1000000
