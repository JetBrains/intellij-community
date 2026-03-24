package com.intellij.lambda.testFramework.testApi

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities


suspend fun mouseLeftClick(component: Component, point: Point? = null) {
  click(component, point, MouseEvent.BUTTON1)
}

suspend fun mouseRightClick(component: Component, point: Point? = null) {
  click(component, point, MouseEvent.BUTTON3)
}

private suspend fun click(component: Component, point: Point?, button: Int) {
  withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
    val eventQueue = IdeEventQueue.getInstance()
    val componentPoint = point ?: Point(component.width / 2, component.height / 2)
    val window = ComponentUtil.getWindow(component)
    val windowPoint = SwingUtilities.convertPoint(component, componentPoint, window)
    eventQueue.postEvent(MouseEvent(window, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.getMaskForButton(button),
                                    windowPoint.x, windowPoint.y, 1, button == MouseEvent.BUTTON3 && !SystemInfoRt.isWindows, button))
    eventQueue.postEvent(MouseEvent(window, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                                    windowPoint.x, windowPoint.y, 1, button == MouseEvent.BUTTON3 && SystemInfoRt.isWindows, button))
    eventQueue.postEvent(MouseEvent(window, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                                    windowPoint.x, windowPoint.y, 1, false, button))
  }
}