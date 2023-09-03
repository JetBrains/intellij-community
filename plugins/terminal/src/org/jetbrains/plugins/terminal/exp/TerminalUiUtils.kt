// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JScrollPane
import kotlin.math.max

object TerminalUiUtils {
  fun createOutputEditor(document: Document, project: Project, settings: JBTerminalSystemSettingsProviderBase): EditorImpl {
    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.isRendererMode = true
    editor.setCustomCursor(this, Cursor.getDefaultCursor())
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false
    editor.gutterComponentEx.setRightFreePaintersAreaWidth(0)

    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = settings.lineSpacing
    }

    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isBlockCursor = true
    }

    editor.installPopupHandler(object:ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent) =
        ActionManager.getInstance().getAction("Terminal.PopupMenu") as ActionGroup})
    return editor
  }

  fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return ensureTermMinimumSize(TermSize(width, height))
  }

  private fun ensureTermMinimumSize(size: TermSize): TermSize {
    return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
  }

  @RequiresEdt(generateAssertion = false)
  fun awaitComponentLayout(component: Component, parentDisposable: Disposable): CompletableFuture<Unit> {
    val size = component.size
    if (size.width > 0 || size.height > 0) {
      return CompletableFuture.completedFuture(Unit)
    }
    if (!UIUtil.isShowing(component, false)) {
      return CompletableFuture.failedFuture(IllegalStateException("component should be showing"))
    }
    val result = CompletableFuture<Unit>()

    val startNano = System.nanoTime()
    val resizeListener: ComponentAdapter = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (LOG.isDebugEnabled) {
          LOG.info("Terminal component layout took " + TimeoutUtil.getDurationMillis(startNano) + "ms")
        }
        result.complete(Unit)
      }
    }
    component.addComponentListener(resizeListener)

    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    alarm.addRequest({
                       result.completeExceptionally(IllegalStateException("Terminal component layout is timed out (>${TIMEOUT}ms)"))
                     }, TIMEOUT, ModalityState.stateForComponent(component))

    Disposer.register(alarm) {
      if (!result.isDone) {
        ApplicationManager.getApplication().invokeLater({
                                                          result.completeExceptionally(IllegalStateException("parent disposed"))
                                                        }, ModalityState.stateForComponent(component))
      }
    }
    result.whenComplete { _, _ ->
      Disposer.dispose(alarm)
      component.removeComponentListener(resizeListener)
    }

    return result
  }

  fun toFloatAndScale(value: Int): Float = JBUIScale.scale(value.toFloat())

  private val LOG = logger<TerminalUiUtils>()
  private const val TIMEOUT = 2000
}