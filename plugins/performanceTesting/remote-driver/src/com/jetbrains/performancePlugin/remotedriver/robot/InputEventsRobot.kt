// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.performancePlugin.remotedriver.waitFor
import org.assertj.swing.awt.AWT.visibleCenterOf
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.timing.Pause.pause
import java.awt.Color
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

internal class InputEventsRobot(
  basicRobot: Robot,
  awtRobot: java.awt.Robot,
) : AbstractIdeRobot(basicRobot, awtRobot) {

  private val pressedModifiers = ConcurrentHashMap.newKeySet<Int>()

  init {
    settings().apply {
      delayBetweenEvents(400) // slow down input events so the UI can keep up (reduces test flakiness)
      simpleWaitForIdle(true)
      timeoutToFindPopup(1000)
    }
  }

  override fun getColor(component: Component, point: Point?): Color {
    if (!StartupUiUtil.isWaylandToolkit()) {
      return super.getColor(component, point)
    }

    val where = point ?: Point(component.width / 2, component.height / 2)
    val image = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    performOnEdt { component.paintAll(image.createGraphics()) }
    @Suppress("UseJBColor")
    return Color(image.getRGB(where.x, where.y), true)
  }

  override fun moveMouse(component: Component) {
    moveMouse(component, visibleCenterOf(component))
  }

  override fun moveMouse(component: Component, point: Point) {
    val window: Window = component.windowAncestor
    val relPoint = convertPoint(component, point, window)
    val keyboardModifiers = currentModifiers()

    // two mouse move events to make selection works with list popups (see com.intellij.ui.popup.list.ListPopupImpl.MyMouseMotionListener#mouseMoved(java.awt.event.MouseEvent))
    postInputEvent(MouseEvent(window, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), keyboardModifiers,
                              relPoint.x + 1, relPoint.y, 0, false, MouseEvent.NOBUTTON))
    postInputEvent(MouseEvent(window, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), keyboardModifiers,
                              relPoint.x, relPoint.y, 0, false, MouseEvent.NOBUTTON))
  }

  override fun moveMouse(c: Component, x: Int, y: Int) {
    moveMouse(c, Point(x, y))
  }

  override fun moveMouse(point: Point) {
    moveMouse(point.x, point.y)
  }

  override fun moveMouse(x: Int, y: Int) {
    val screenPoint = Point(x, y)
    val window = performOnEdt {
      Window.getWindows().lastOrNull { w -> w.isShowing && w.bounds.contains(screenPoint) }
    } ?: error("No window found for point $screenPoint")

    postInputEvent(MouseEvent(window, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), currentModifiers(), x - window.x, y - window.y, 0, false, MouseEvent.NOBUTTON))
  }

  override fun click(where: Point, mouseButton: MouseButton, times: Int) {
    throw UnsupportedOperationException("clicking on arbitrary point is not supported, see AT-4884")
  }

  override fun pressKey(keyCode: Int) {
    postInputEvent(KeyEvent(waitForFocusOwner(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), currentModifiers(), keyCode, KeyEvent.CHAR_UNDEFINED))
    pressedModifiers.add(keyCode)
  }

  override fun releaseKey(keyCode: Int) {
    postInputEvent(KeyEvent(waitForFocusOwner(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), currentModifiers(), keyCode, KeyEvent.CHAR_UNDEFINED))
    pressedModifiers.remove(keyCode)
  }

  override fun type(character: Char) {
    postInputEvent(KeyEvent(waitForFocusOwner(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), currentModifiers(), KeyEvent.VK_UNDEFINED, character))
  }

  override fun enterText(text: String) {
    text.forEach { type(it) }
  }

  override fun strictClick(component: Component, point: Point?) {
    click(component, point ?: visibleCenterOf(component), MouseButton.LEFT_BUTTON, 1)
  }

  override fun dragAndDrop(fromComponent: Component, fromPoint: Point, toComponent: Component, toPoint: Point) {
    val fromWindow: Window = fromComponent.windowAncestor
    val toWindow: Window = toComponent.windowAncestor
    val fromRel = convertPoint(fromComponent, fromPoint, fromWindow)
    val toRel = convertPoint(toComponent, toPoint, toWindow)

    val fromScreenPos = performOnEdt { fromWindow.locationOnScreen }!!
    val toScreenPos = if (toWindow === fromWindow) fromScreenPos else performOnEdt { toWindow.locationOnScreen }!!

    @Suppress("DEPRECATION")
    val pressModifiers = currentModifiers() or InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON1_MASK
    @Suppress("DEPRECATION")
    val releaseModifiers = currentModifiers() or InputEvent.BUTTON1_MASK

    postInputEvent(MouseEvent(fromWindow, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), currentModifiers(), fromRel.x, fromRel.y, fromScreenPos.x + fromRel.x, fromScreenPos.y + fromRel.y, 0, false, MouseEvent.NOBUTTON))
    postInputEvent(MouseEvent(fromWindow, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), pressModifiers, fromRel.x, fromRel.y, fromScreenPos.x + fromRel.x, fromScreenPos.y + fromRel.y, 1, false, MouseEvent.BUTTON1))
    pauseBetweenEvents()
    // Two drag events are required: the first exits the dead zone and sets dragSource (isDragOut
    // checks dragSource == null and returns false); the second triggers isDragOut with dragSource set.
    postInputEvent(MouseEvent(toWindow, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), pressModifiers, toRel.x, toRel.y, toScreenPos.x + toRel.x, toScreenPos.y + toRel.y, 1, false, MouseEvent.NOBUTTON))
    pauseBetweenEvents()
    postInputEvent(MouseEvent(toWindow, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), pressModifiers, toRel.x, toRel.y, toScreenPos.x + toRel.x, toScreenPos.y + toRel.y, 1, false, MouseEvent.NOBUTTON))
    pauseBetweenEvents()
    postInputEvent(MouseEvent(toWindow, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), releaseModifiers, toRel.x, toRel.y, toScreenPos.x + toRel.x, toScreenPos.y + toRel.y, 1, false, MouseEvent.BUTTON1))
  }

  override fun doClick(component: Component, where: Point?, mouseButton: MouseButton, clickCount: Int) {
    postClickEvent(component, mouseButton, clickCount, where)
  }

  override fun doPressAndReleaseKey(keyCode: Int, modifiers: Int) {
    postKeyEvents(keyCode, toExtendedModifiers(modifiers))
  }

  private fun postClickEvent(component: Component, button: MouseButton = MouseButton.LEFT_BUTTON, clickCount: Int = 1, where: Point? = null) {
    val awtBtn = button.awtButton
    val buttonDownMask = when (button) {
      MouseButton.LEFT_BUTTON -> InputEvent.BUTTON1_DOWN_MASK
      MouseButton.RIGHT_BUTTON -> InputEvent.BUTTON3_DOWN_MASK
      MouseButton.MIDDLE_BUTTON -> InputEvent.BUTTON2_DOWN_MASK
    }
    @Suppress("DEPRECATION")
    val buttonLegacyMask = when (button) {
      MouseButton.LEFT_BUTTON -> InputEvent.BUTTON1_MASK
      MouseButton.RIGHT_BUTTON -> InputEvent.BUTTON3_MASK
      MouseButton.MIDDLE_BUTTON -> InputEvent.BUTTON2_MASK
    }
    val window: Window = component.windowAncestor
    val clickPoint: Point = convertPoint(component, where ?: visibleCenterOf(component), window)
    val keyboardModifiers = currentModifiers()

    moveMouse(window, clickPoint)

    for (i in 1..clickCount) {
      postInputEvent(MouseEvent(window, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), keyboardModifiers or buttonDownMask or buttonLegacyMask,
                                clickPoint.x, clickPoint.y, i, awtBtn == MouseEvent.BUTTON3, awtBtn))
      pauseBetweenEvents()
      postInputEvent(MouseEvent(window, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), keyboardModifiers or buttonLegacyMask,
                                clickPoint.x, clickPoint.y, i, false, awtBtn))
      postInputEvent(MouseEvent(window, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), keyboardModifiers or buttonLegacyMask,
                                clickPoint.x, clickPoint.y, i, false, awtBtn))
    }
  }

  private fun postKeyEvents(keyCode: Int, modifiers: Int = 0) {
    val focusOwner = waitForFocusOwner()
    val fullModifiers = modifiers or currentModifiers()
    val allModifierKeys = modifierKeyCodes(fullModifiers)
    val modifiersToPressKeys = allModifierKeys.filter { it !in pressedModifiers }

    modifiersToPressKeys.forEach { modKey ->
      postInputEvent(KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, modKey, KeyEvent.CHAR_UNDEFINED))
    }
    if (keyCode !in allModifierKeys) {
      postInputEvent(KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), fullModifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
      pauseBetweenEvents()
      val keyChar = keyCharForTyped(keyCode, fullModifiers and InputEvent.SHIFT_DOWN_MASK != 0)
      if (keyChar != null) {
        postInputEvent(KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), fullModifiers, KeyEvent.VK_UNDEFINED, keyChar))
      }
      postInputEvent(KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), fullModifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
    }
    modifiersToPressKeys.reversed().forEach { modKey ->
      postInputEvent(KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, modKey, KeyEvent.CHAR_UNDEFINED))
    }
  }

  private fun keyCharForTyped(keyCode: Int, shift: Boolean): Char? = when (keyCode) {
    in KeyEvent.VK_A..KeyEvent.VK_Z -> ('a' + (keyCode - KeyEvent.VK_A)).let { if (shift) it.uppercaseChar() else it }
    in KeyEvent.VK_0..KeyEvent.VK_9 -> '0' + (keyCode - KeyEvent.VK_0)
    else -> null
  }

  private fun pauseBetweenEvents() {
    pause(settings().delayBetweenEvents().toLong())
  }

  private fun waitForFocusOwner(): Component {
    var focusOwner: Component? = null
    waitFor(3.seconds.toJavaDuration(), 200.milliseconds.toJavaDuration(), "No focus owner found") {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      focusOwner != null
    }
    return focusOwner!!
  }

  private fun convertPoint(component: Component, point: Point, window: Window): Point =
    if (component === window) point else SwingUtilities.convertPoint(component, point, window)

  private fun postInputEvent(inputEvent: InputEvent) {
    IdeEventQueue.getInstance().postEvent(inputEvent)
    ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
  }

  private fun currentModifiers(): Int {
    var mask = 0
    for (keyCode in pressedModifiers) {
      mask = mask or when (keyCode) {
        KeyEvent.VK_SHIFT -> InputEvent.SHIFT_DOWN_MASK
        KeyEvent.VK_CONTROL -> InputEvent.CTRL_DOWN_MASK
        KeyEvent.VK_ALT -> InputEvent.ALT_DOWN_MASK
        KeyEvent.VK_META -> InputEvent.META_DOWN_MASK
        KeyEvent.VK_ALT_GRAPH -> InputEvent.ALT_GRAPH_DOWN_MASK
        else -> 0
      }
    }
    return mask
  }

  private fun modifierKeyCodes(extendedModifiers: Int): Set<Int> = buildSet {
    if (extendedModifiers and InputEvent.SHIFT_DOWN_MASK != 0) add(KeyEvent.VK_SHIFT)
    if (extendedModifiers and InputEvent.CTRL_DOWN_MASK != 0) add(KeyEvent.VK_CONTROL)
    if (extendedModifiers and InputEvent.ALT_DOWN_MASK != 0) add(KeyEvent.VK_ALT)
    if (extendedModifiers and InputEvent.META_DOWN_MASK != 0) add(KeyEvent.VK_META)
    if (extendedModifiers and InputEvent.ALT_GRAPH_DOWN_MASK != 0) add(KeyEvent.VK_ALT_GRAPH)
  }

  @Suppress("DEPRECATION")
  private fun toExtendedModifiers(modifiers: Int): Int {
    var result = modifiers and (InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or
                                InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK)
    if (modifiers and InputEvent.SHIFT_MASK != 0) result = result or InputEvent.SHIFT_DOWN_MASK
    if (modifiers and InputEvent.CTRL_MASK != 0) result = result or InputEvent.CTRL_DOWN_MASK
    if (modifiers and InputEvent.ALT_MASK != 0) result = result or InputEvent.ALT_DOWN_MASK
    if (modifiers and InputEvent.META_MASK != 0) result = result or InputEvent.META_DOWN_MASK
    if (modifiers and InputEvent.ALT_GRAPH_MASK != 0) result = result or InputEvent.ALT_GRAPH_DOWN_MASK
    return result
  }

  private val Component.windowAncestor: Window get() = checkNotNull(this as? Window ?: SwingUtilities.getWindowAncestor(this)) { "$this is detached from the window" }
}
