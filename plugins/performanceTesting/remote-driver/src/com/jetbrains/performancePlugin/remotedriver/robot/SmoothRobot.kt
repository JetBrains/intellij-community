// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.driver.model.RemoteMouseButton
import com.jetbrains.performancePlugin.remotedriver.waitFor
import org.assertj.swing.awt.AWT.translate
import org.assertj.swing.awt.AWT.visibleCenterOf
import org.assertj.swing.core.*
import org.assertj.swing.core.Robot
import org.assertj.swing.core.Scrolling.scrollToVisible
import org.assertj.swing.edt.GuiActionRunner.execute
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.hierarchy.ComponentHierarchy
import org.assertj.swing.keystroke.KeyStrokeMap
import org.assertj.swing.timing.Pause.pause
import org.assertj.swing.util.Modifiers
import java.awt.*
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.math.ln

internal class SmoothRobot : Robot {

  private val basicRobot: BasicRobot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock() as BasicRobot

  private val waitConst = 30L
  private var myAwareClick: Boolean = false
  private val fastRobot: java.awt.Robot = java.awt.Robot()

  init {
    settings().delayBetweenEvents(10)
    basicRobot.settings().simpleWaitForIdle(true)
  }

  override fun moveMouse(component: Component) {
    var where = visibleCenterOf(component)
    if (component is JComponent) {
      scrollToVisible(this, component)
      where = visibleCenterOf(component)
    }
    moveMouse(component, where)
  }

  override fun moveMouse(component: Component, point: Point) {
    val translatedPoint = execute<Point?> { translate(component, point.x, point.y) }
    requireNotNull(translatedPoint) { "Translated point should be not null" }
    moveMouse(translatedPoint.x, translatedPoint.y)
  }

  override fun moveMouse(point: Point) {
    moveMouse(point.x, point.y)
  }


  override fun click(component: Component) {
    moveMouse(component)
    basicRobot.click(component)
  }

  private fun RemoteMouseButton.toAssertJ() = when(this) {
    RemoteMouseButton.LEFT -> MouseButton.LEFT_BUTTON
    RemoteMouseButton.MIDDLE -> MouseButton.MIDDLE_BUTTON
    RemoteMouseButton.RIGHT -> MouseButton.RIGHT_BUTTON
  }

  fun click(component: Component, mouseButton: RemoteMouseButton) {
    click(component, mouseButton.toAssertJ())
  }
  override fun click(component: Component, mouseButton: MouseButton) {
    moveMouse(component)
    basicRobot.click(component, mouseButton)
  }

  fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int) {
    click(component, mouseButton.toAssertJ(), counts)
  }
  override fun click(component: Component, mouseButton: MouseButton, counts: Int) {
    moveMouse(component)
    basicRobot.click(component, mouseButton, counts)
  }

  override fun click(component: Component, point: Point) {
    moveMouse(component, point)
    basicRobot.click(component, point)
  }

  override fun showWindow(window: Window) {
    basicRobot.showWindow(window)
  }

  override fun showWindow(window: Window, dimension: Dimension) {
    basicRobot.showWindow(window, dimension)
  }

  override fun showWindow(window: Window, dimension: Dimension?, p2: Boolean) {
    basicRobot.showWindow(window, dimension, p2)
  }

  override fun isActive(): Boolean = basicRobot.isActive


  override fun pressAndReleaseKey(p0: Int, vararg p1: Int) {
    basicRobot.pressAndReleaseKey(p0, *p1)
  }

  override fun showPopupMenu(component: Component): JPopupMenu =
    basicRobot.showPopupMenu(component)

  override fun showPopupMenu(component: Component, point: Point): JPopupMenu =
    basicRobot.showPopupMenu(component, point)

  override fun jitter(component: Component) {
    basicRobot.jitter(component)
  }

  override fun jitter(component: Component, point: Point) {
    basicRobot.jitter(component, point)
  }

  override fun pressModifiers(p0: Int) {
    basicRobot.pressModifiers(p0)
  }

  fun pressMouse(mouseButton: RemoteMouseButton) {
    pressMouse(mouseButton.toAssertJ())
  }
  override fun pressMouse(mouseButton: MouseButton) {
    basicRobot.pressMouse(mouseButton)
  }

  override fun pressMouse(component: Component, point: Point) {
    moveMouse(component, point)
    basicRobot.pressMouse(component, point)
  }

  fun pressMouse(component: Component, point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(component, point, mouseButton.toAssertJ())
  }
  override fun pressMouse(component: Component, point: Point, mouseButton: MouseButton) {
    moveMouse(component, point)
    basicRobot.pressMouse(component, point, mouseButton)
  }

  fun pressMouse(point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(point, mouseButton.toAssertJ())
  }
  override fun pressMouse(point: Point, mouseButton: MouseButton) {
    moveMouse(point)
    basicRobot.pressMouse(point, mouseButton)
  }

  override fun hierarchy(): ComponentHierarchy =
    basicRobot.hierarchy()

  override fun releaseKey(p0: Int) {
    basicRobot.releaseKey(p0)
  }

  override fun isDragging(): Boolean = basicRobot.isDragging

  override fun printer(): ComponentPrinter = basicRobot.printer()

  override fun type(char: Char) {
    basicRobot.type(char)
  }

  override fun requireNoJOptionPaneIsShowing() {
    basicRobot.requireNoJOptionPaneIsShowing()
  }

  override fun cleanUp() {
    basicRobot.cleanUp()
  }

  fun releaseMouse(mouseButton: RemoteMouseButton) {
    releaseMouse(mouseButton.toAssertJ())
  }
  override fun releaseMouse(mouseButton: MouseButton) {
    basicRobot.releaseMouse(mouseButton)
  }

  override fun pressKey(p0: Int) {
    basicRobot.pressKey(p0)
  }

  override fun settings(): Settings = basicRobot.settings()

  override fun enterText(text: String) {
    basicRobot.enterText(text)
  }

  override fun releaseMouseButtons() {
    basicRobot.releaseMouseButtons()
  }

  override fun rightClick(component: Component) {
    moveMouse(component)
    basicRobot.rightClick(component)
  }

  override fun focus(component: Component) {
    basicRobot.focus(component)
  }

  override fun doubleClick(component: Component) {
    moveMouse(component)
    basicRobot.doubleClick(component)
  }

  override fun cleanUpWithoutDisposingWindows() {
    basicRobot.cleanUpWithoutDisposingWindows()
  }

  override fun isReadyForInput(component: Component): Boolean = basicRobot.isReadyForInput(component)

  override fun focusAndWaitForFocusGain(component: Component) {
    basicRobot.focusAndWaitForFocusGain(component)
  }

  override fun releaseModifiers(p0: Int) {
    basicRobot.releaseModifiers(p0)
  }

  override fun findActivePopupMenu(): JPopupMenu? =
    basicRobot.findActivePopupMenu()

  override fun rotateMouseWheel(component: Component, p1: Int) {
    basicRobot.rotateMouseWheel(component, p1)
  }

  override fun rotateMouseWheel(p0: Int) {
    basicRobot.rotateMouseWheel(p0)
  }

  override fun pressAndReleaseKeys(vararg p0: Int) {
    basicRobot.pressAndReleaseKeys(*p0)
  }

  override fun finder(): ComponentFinder = basicRobot.finder()

  override fun waitForIdle() {
    if (myAwareClick) {
      Thread.sleep(50)
    }
    else {
      pause(waitConst)
      if (!isEdt()) basicRobot.waitForIdle()
    }
  }

  override fun close(w: Window) {
    basicRobot.close(w)
    basicRobot.waitForIdle()
  }

  //smooth mouse move
  override fun moveMouse(x: Int, y: Int) {
    val pauseConstMs = settings().delayBetweenEvents().toLong()
    val n = 20
    val start = MouseInfo.getPointerInfo().location
    val dx = (x - start.x) / n.toDouble()
    val dy = (y - start.y) / n.toDouble()
    for (step in 1..n) {
      try {
        pause(pauseConstMs)
      }
      catch (e: InterruptedException) {
        e.printStackTrace()
      }

      basicRobot.moveMouse(
        (start.x + dx * ((ln(1.0 * step / n) - ln(1.0 / n)) * n / (0 - ln(1.0 / n)))).toInt(),
        (start.y + dy * ((ln(1.0 * step / n) - ln(1.0 / n)) * n / (0 - ln(1.0 / n)))).toInt()
      )
    }
    basicRobot.moveMouse(x, y)
  }

  //smooth mouse move to component
  override fun moveMouse(c: Component, x: Int, y: Int) {
    moveMouseWithAttempts(c, x, y)
  }

  //smooth mouse move for find and click actions
  fun click(c: Component, where: Point, button: RemoteMouseButton, times: Int) {
    click(c, where, button.toAssertJ(), times)
  }
  override fun click(c: Component, where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(c, where, button, times)
  }

  //we are replacing BasicRobot click with our click because the original one cannot handle double click rightly (BasicRobot creates unnecessary move event between click event which breaks clickCount from 2 to 1)
  fun click(where: Point, button: RemoteMouseButton, times: Int) {
    click(where, button.toAssertJ(), times)
  }
  override fun click(where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(null, where, button, times)
  }

  override fun pressKeyWhileRunning(keyCode: Int, runnable: Runnable?) {
    basicRobot.pressKeyWhileRunning(keyCode, runnable)
  }

  override fun pressMouseWhileRunning(button: MouseButton?, runnable: Runnable?) {
    basicRobot.pressMouseWhileRunning(button, runnable)
  }

  override fun pressMouseWhileRunning(c: Component?, where: Point?, runnable: Runnable?) {
    basicRobot.pressMouseWhileRunning(c, where, runnable)
  }

  override fun pressMouseWhileRunning(c: Component?, where: Point?, button: MouseButton?, runnable: Runnable?) {
    basicRobot.pressMouseWhileRunning(c, where, button, runnable)
  }

  override fun pressMouseWhileRunning(where: Point?, button: MouseButton?, runnable: Runnable?) {
    basicRobot.pressMouseWhileRunning(where, button, runnable)
  }

  override fun pressModifiersWhileRunning(modifierMask: Int, runnable: Runnable?) {
    basicRobot.pressModifiersWhileRunning(modifierMask, runnable)
  }

  private fun fastPressAndReleaseKey(keyCode: Int, vararg modifiers: Int) {
    val unifiedModifiers = unify(*modifiers)
    val updatedModifiers = Modifiers.updateModifierWithKeyCode(keyCode, unifiedModifiers)
    fastPressModifiers(updatedModifiers)
    if (updatedModifiers == unifiedModifiers) {
      fastPressKey(keyCode)
      fastReleaseKey(keyCode)
    }
    fastReleaseModifiers(updatedModifiers)
  }

  fun fastPressAndReleaseModifiers(vararg modifiers: Int) {
    val unifiedModifiers = unify(*modifiers)
    fastPressModifiers(unifiedModifiers)
    pause(50)
    fastReleaseModifiers(unifiedModifiers)
  }

  private fun fastPressAndReleaseKeyWithoutModifiers(keyCode: Int) {
    fastPressKey(keyCode)
    fastReleaseKey(keyCode)
  }

  fun shortcut(keyStoke: KeyStroke) {
    fastPressAndReleaseKey(keyStoke.keyCode, keyStoke.modifiers)
  }

  fun shortcutAndTypeString(keyStoke: KeyStroke, string: String, delayBetweenShortcutAndTypingMs: Int = 0) {
    fastPressAndReleaseKey(keyStoke.keyCode, keyStoke.modifiers)
    fastTyping(string, delayBetweenShortcutAndTypingMs)
  }

  private fun fastTyping(string: String, delayBetweenShortcutAndTypingMs: Int = 0) {
    val keyCodeArray: IntArray = string
      .map { KeyStrokeMap.keyStrokeFor(it)?.keyCode ?: throw Exception("Unable to get keystroke for char '$it'") }
      .toIntArray()
    if (delayBetweenShortcutAndTypingMs > 0) pause(delayBetweenShortcutAndTypingMs.toLong())
    keyCodeArray.forEach { fastPressAndReleaseKeyWithoutModifiers(keyCode = it); pause(50) }
  }

  fun makeScreenshot(): ByteArray = makeScreenshot(Rectangle(Toolkit.getDefaultToolkit().screenSize))

  private fun makeScreenshot(screenshotArea: Rectangle): ByteArray {
    return ByteArrayOutputStream().use { b ->
      ImageIO.write(fastRobot.createScreenCapture(screenshotArea), "png", b)
      b.toByteArray()
    }
  }

  private fun isEdt() = SwingUtilities.isEventDispatchThread()

  private fun moveMouseAndClick(c: Component? = null, where: Point, button: MouseButton, times: Int) {
    if (c != null) moveMouse(c, where.x, where.y) else moveMouse(where.x, where.y)
    //pause between moving cursor and performing a click.
    pause(waitConst)
    myEdtAwareClick(button, times, where, c)
  }

  private fun moveMouseWithAttempts(c: Component, x: Int, y: Int, attempts: Int = 3) {
    if (attempts == 0) return
    waitFor(Duration.ofSeconds(5), Duration.ofSeconds(1)) { performOnEdt { c.isShowing }!! }

    val componentLocation: Point = requireNotNull(performOnEdt { translate(c, x, y) })
    moveMouse(componentLocation.x, componentLocation.y)

    val componentLocationAfterMove: Point = requireNotNull(performOnEdt { translate(c, x, y) })
    val mouseLocation = MouseInfo.getPointerInfo().location
    if (mouseLocation.x != componentLocationAfterMove.x || mouseLocation.y != componentLocationAfterMove.y)
      moveMouseWithAttempts(c, x, y, attempts - 1)
  }

  private fun myInnerClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    if (component == null)
      basicRobot.click(point, button, times)
    else
      basicRobot.click(component, point, button, times)
  }

  private fun fastPressKey(keyCode: Int) {
    fastRobot.keyPress(keyCode)
  }

  private fun fastReleaseKey(keyCode: Int) {
    fastRobot.keyRelease(keyCode)
  }

  private fun fastPressModifiers(modifierMask: Int) {
    val keys = Modifiers.keysFor(modifierMask)
    val keysSize = keys.size
    (0 until keysSize)
      .map { keys[it] }
      .forEach { fastPressKey(it) }
  }

  private fun fastReleaseModifiers(modifierMask: Int) {
    val modifierKeys = Modifiers.keysFor(modifierMask)
    for (i in modifierKeys.indices.reversed())
      fastReleaseKey(modifierKeys[i])
  }

  private fun myEdtAwareClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    if (isEdt()) {
      thread { myInnerClick(button, times, point, component) } // as AssertJ doesn't allow to waitForIdle on EDT
    }
    else {
      myInnerClick(button, times, point, component)
    }
    waitForIdle()
  }

  private fun <T> performOnEdt(body: () -> T): T? =
    execute(object : GuiQuery<T>() {
      override fun executeInEDT() = body.invoke()
    })

  private fun awareClick(body: () -> Unit) {
    myAwareClick = true
    body.invoke()
    myAwareClick = false
  }

  private fun unify(vararg modifiers: Int): Int {
    var unified = 0
    if (modifiers.isNotEmpty()) {
      unified = modifiers[0]
      for (i in 1 until modifiers.size) {
        unified = unified or modifiers[i]
      }
    }
    return unified
  }
}