// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.xray.frontend.impl.service.XRayModeService
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JScrollPane
import javax.swing.JViewport

internal val XRAY_MOUSE_WHEEL_PATCHER_DISPOSABLE: Key<Disposable?> = Key.create<Disposable>("xray.editor.mousewheel.patcher.disposable")

internal fun attachMouseWheelPatcher(editor: Editor) {
  if (editor.getUserData(XRAY_MOUSE_WHEEL_PATCHER_DISPOSABLE) != null) return

  val mouseWheelPatcherDisposable = Disposer.newDisposable(XRayModeService.getInstance(), "XRayMouseWheelPatcher")
  editor.putUserData(XRAY_MOUSE_WHEEL_PATCHER_DISPOSABLE, mouseWheelPatcherDisposable)

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

  Disposer.register(mouseWheelPatcherDisposable) {
    when (val viewPort = editor.contentComponent.parent) {
      is JViewport -> when (val scrollPane = viewPort.parent) {
        is JScrollPane -> scrollPane.removeMouseWheelListener(editorMouseWheelListener)
      }
    }
  }
}

internal fun detachMouseWheelPatcher(editor: Editor) {
  val disposable = editor.getUserData(XRAY_MOUSE_WHEEL_PATCHER_DISPOSABLE) ?: return
  editor.putUserData(XRAY_MOUSE_WHEEL_PATCHER_DISPOSABLE, null)
  Disposer.dispose(disposable)
}


internal class XRayEditorMouseWheelPatcher : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    attachMouseWheelPatcher(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    detachMouseWheelPatcher(event.editor)
  }
}
