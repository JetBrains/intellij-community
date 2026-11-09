// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.editorTracking

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.xray.frontend.api.editorTracking.XRAY_CURRENT_LINE
import com.intellij.xray.frontend.api.editorTracking.XRAY_CURRENT_LINE_POSITION
import com.intellij.xray.frontend.api.editorTracking.XRAY_EDITOR_MODE
import com.intellij.xray.frontend.api.editorTracking.XRAY_EDITOR_USE_MODE
import com.intellij.xray.frontend.api.editorTracking.XRayEditorUseMode
import com.intellij.xray.frontend.api.editorTracking.XRayTimings
import com.intellij.xray.frontend.impl.service.XRayModeService
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.Timer

internal fun attachXRayTracker(editor: Editor) {
  if (editor.getUserData(XRAY_TRACKER_DISPOSABLE) != null) return

  val trackerDisposable = Disposer.newDisposable(XRayEditorTrackingService.getInstance(), "XRayModeTracker")
  editor.putUserData(XRAY_TRACKER_DISPOSABLE, trackerDisposable)
  editor.putUserData(XRAY_EDITOR_USE_MODE, XRayEditorUseMode.READING)

  // Caret listener for X-ray mode
  val caretRefreshTimer = Timer(XRayTimings.CARET_REFRESH_DELAY_MS) {
    val service = XRayModeService.getInstance()

    if (service.isXRayModeEnabled() &&
        editor.getUserData(XRAY_EDITOR_USE_MODE) == XRayEditorUseMode.EDITING) {
      service.refreshActiveEditorHints(editor)
    }
  }.also { it.isRepeats = false }

  val editorCaretListener = object : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      editor.putUserData(XRAY_CURRENT_LINE, event.newPosition.line)
      editor.putUserData(XRAY_CURRENT_LINE_POSITION, event.newPosition.column)
      val service = XRayModeService.getInstance()
      if (service.isXRayModeEnabled()) {
        caretRefreshTimer.restart()
      }
    }
  }
  editor.caretModel.addCaretListener(editorCaretListener, trackerDisposable)

  // Document listener for X-ray mode
  val lastDocumentChangeTimer = Timer(XRayTimings.TYPING_DELAY_MS) {
    editor.putUserData(XRAY_EDITOR_USE_MODE, XRayEditorUseMode.READING)
    ActionToolbarImpl.updateAllToolbarsImmediately(false)

    val service = XRayModeService.getInstance()
    service.refreshActiveEditorHints(editor)
  }.also { it.isRepeats = false }

  // Key listener for X-ray mode
  val keyListener = object : KeyAdapter() {
    override fun keyReleased(e: KeyEvent?) {

      val service = XRayModeService.getInstance()

      if (!service.isXRayModeEnabled()) return

      if (e == null || e.keyCode !in NAVIGATION_KEYS) return

      val oldUseMode = editor.getUserData(XRAY_EDITOR_USE_MODE)
      editor.putUserData(XRAY_EDITOR_USE_MODE, XRayEditorUseMode.EDITING)

      if (oldUseMode != XRayEditorUseMode.EDITING) {
        ActionToolbarImpl.updateAllToolbarsImmediately(false)
      }

      lastDocumentChangeTimer.restart()
    }
  }
  editor.contentComponent.addKeyListener(keyListener)

  // Document listener
  val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      val oldUseMode = editor.getUserData(XRAY_EDITOR_USE_MODE)
      editor.putUserData(XRAY_EDITOR_USE_MODE, XRayEditorUseMode.EDITING)

      if (oldUseMode != XRayEditorUseMode.EDITING) {
        ActionToolbarImpl.updateAllToolbarsImmediately(false)
      }

      lastDocumentChangeTimer.restart()
    }
  }
  editor.document.addDocumentListener(documentListener, trackerDisposable)

  // Mouse wheel listener to allow scrolling when the combination is pressed
  // The implementation reflects the solution from JetBrains Scala plugin (ScalaEditorFactoryListener#editorMouseWheelListener)
  val editorMouseWheelListener = object : MouseWheelListener {
    override fun mouseWheelMoved(e: MouseWheelEvent) {
      val service = XRayModeService.getInstance()

      if (!service.isHoldActive()) return

      if (!e.isShiftDown || !e.isControlDown) return

      e.consume()
      val event = MouseWheelEvent(
        e.source as Component, e.id, e.`when`,
        e.modifiersEx and InputEvent.CTRL_DOWN_MASK.inv() and InputEvent.SHIFT_DOWN_MASK.inv() and
          (InputEvent.META_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK).inv(),
        e.x, e.y, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger, e.scrollType, e.scrollAmount,
        e.wheelRotation, e.preciseWheelRotation
      )
      Toolkit.getDefaultToolkit().systemEventQueue.postEvent(event)
    }
  }

  when (val viewPort = editor.contentComponent.parent) {
    is JViewport -> when (val scrollPane = viewPort.parent) {
      is JScrollPane -> scrollPane.addMouseWheelListener(editorMouseWheelListener)
    }
  }

  Disposer.register(trackerDisposable) {
    editor.contentComponent.removeKeyListener(keyListener)

    when (val viewPort = editor.contentComponent.parent) {
      is JViewport -> when (val scrollPane = viewPort.parent) {
        is JScrollPane -> scrollPane.removeMouseWheelListener(editorMouseWheelListener)
      }
    }

    caretRefreshTimer.stop()
    lastDocumentChangeTimer.stop()

    editor.putUserData(XRAY_EDITOR_MODE, null)
    editor.putUserData(XRAY_EDITOR_USE_MODE, null)
    editor.putUserData(XRAY_CURRENT_LINE, null)
    editor.putUserData(XRAY_CURRENT_LINE_POSITION, null)
  }
}

internal fun detachXRayTracker(editor: Editor) {
  val disposable = editor.getUserData(XRAY_TRACKER_DISPOSABLE) ?: return
  editor.putUserData(XRAY_TRACKER_DISPOSABLE, null)
  Disposer.dispose(disposable)
}

internal class XRayModeTracker : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    attachXRayTracker(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    detachXRayTracker(event.editor)
  }
}

internal val XRAY_TRACKER_DISPOSABLE: Key<Disposable?> = Key.create<Disposable>("xray.tracker.disposable")
internal val NAVIGATION_KEYS: IntArray = intArrayOf(KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP, KeyEvent.VK_DOWN)
