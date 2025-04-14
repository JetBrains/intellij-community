// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.performancePlugin.remotedriver.waitFor
import org.assertj.swing.awt.AWT.translate
import org.assertj.swing.awt.AWT.visibleCenterOf
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.core.Scrolling.scrollToVisible
import org.assertj.swing.edt.GuiActionRunner.execute
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.timing.Pause.pause
import org.assertj.swing.util.Modifiers
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.ln

internal class SmoothRobot @JvmOverloads constructor(
  private val basicRobot: Robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock(),
  private val awtRobot: java.awt.Robot = Robot(),
) : Robot by basicRobot {

  init {
    settings().apply {
      delayBetweenEvents(10)
      simpleWaitForIdle(true)
      timeoutToFindPopup(1000)
    }
  }

  fun getColor(component: Component, point: Point? = null): Color {
    var where = point
    if (where == null) {
      where = visibleCenterOf(component)
    }
    val translatedPoint = performOnEdt { translate(component, where!!.x, where!!.y) }
    checkNotNull(translatedPoint) { "Translated point should be not null" }
    return awtRobot.getPixelColor(translatedPoint.x, translatedPoint.y)
  }

  override fun moveMouse(component: Component) {
    if (component is JComponent) {
      scrollToVisible(this, component)
    }
    val where = visibleCenterOf(component)
    moveMouse(component, where)
  }

  override fun moveMouse(component: Component, point: Point) {
    val translatedPoint = performOnEdt { translate(component, point.x, point.y) }
    checkNotNull(translatedPoint) { "Translated point should be not null" }
    moveMouse(translatedPoint.x, translatedPoint.y)
  }

  //smooth mouse move to component
  override fun moveMouse(c: Component, x: Int, y: Int) {
    moveMouseWithAttempts(c, x, y)
  }

  override fun moveMouse(point: Point) {
    moveMouse(point.x, point.y)
  }

  override fun moveMouse(x: Int, y: Int) {
    smoothMoveMouse(x, y)
  }

  override fun click(component: Component) {
    click(component, MouseButton.LEFT_BUTTON)
  }

  fun click(component: Component, mouseButton: RemoteMouseButton) {
    click(component, mouseButton.toAssertJ())
  }

  override fun click(component: Component, mouseButton: MouseButton) {
    click(component, mouseButton, 1)
  }

  override fun click(component: Component, point: Point) {
    click(component, point, MouseButton.LEFT_BUTTON, 1)
  }

  fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int) {
    click(component, mouseButton.toAssertJ(), counts)
  }

  override fun click(component: Component, mouseButton: MouseButton, counts: Int) {
    clickWithRetry(component, null, mouseButton, counts)
  }

  fun click(c: Component, where: Point, button: RemoteMouseButton, times: Int) {
    click(c, where, button.toAssertJ(), times)
  }

  fun click(where: Point, button: RemoteMouseButton, times: Int) {
    click(where, button.toAssertJ(), times)
  }

  //we are replacing BasicRobot click with our click because the original one cannot handle double click rightly (BasicRobot creates unnecessary move event between click event which breaks clickCount from 2 to 1)
  override fun click(c: Component, where: Point, button: MouseButton, times: Int) {
    clickWithRetry(c, where, button, times)
  }

  override fun click(where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(where, button, times)
  }

  override fun doubleClick(c: Component) {
    click(c, MouseButton.LEFT_BUTTON, 2)
  }

  fun pressMouse(mouseButton: RemoteMouseButton) {
    pressMouse(mouseButton.toAssertJ())
  }

  fun pressMouse(component: Component, point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(component, point, mouseButton.toAssertJ())
  }

  fun pressMouse(point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(point, mouseButton.toAssertJ())
  }

  override fun pressMouse(component: Component, point: Point) {
    moveMouse(component, point)
    basicRobot.pressMouse(component, point)
  }

  override fun pressMouse(component: Component, point: Point, mouseButton: MouseButton) {
    moveMouse(component, point)
    basicRobot.pressMouse(component, point, mouseButton)
  }

  override fun pressMouse(point: Point, mouseButton: MouseButton) {
    moveMouse(point)
    basicRobot.pressMouse(point, mouseButton)
  }

  fun releaseMouse(mouseButton: RemoteMouseButton) {
    releaseMouse(mouseButton.toAssertJ())
  }

  fun doubleKey(keyCode: Int) {
    awtRobot.keyPress(keyCode)
    awtRobot.keyRelease(keyCode)
    Thread.sleep(10)
    awtRobot.keyPress(keyCode)
    awtRobot.keyRelease(keyCode)
  }

  fun doublePressKeyAndHold(key: Int) {
    awtRobot.keyPress(key)
    awtRobot.keyRelease(key)
    Thread.sleep(10)
    awtRobot.keyPress(key)
  }

  override fun rightClick(component: Component) {
    if (useInputEvents()) {
      postClickEvent(component, button = MouseButton.RIGHT_BUTTON)
    }
    else {
      moveMouse(component)
      basicRobot.rightClick(component)
    }
  }

  fun shortcut(keyStoke: KeyStroke) {
    fastPressAndReleaseKey(keyStoke.keyCode, keyStoke.modifiers)
  }

  fun selectAndDrag(component: Component, from: Point, to: Point, delayMs: Int) {
    moveMouse(component, from)

    click(component, from)
    pressMouse(RemoteMouseButton.LEFT)

    Thread.sleep(delayMs.toLong())
    moveMouse(component, to)

    Thread.sleep(delayMs.toLong())
    releaseMouse(RemoteMouseButton.LEFT)
  }

  fun makeScreenshot(): ByteArray = makeScreenshot(Rectangle(Toolkit.getDefaultToolkit().screenSize))

  private fun smoothMoveMouse(x: Int, y: Int) {
    val pauseConstMs = settings().delayBetweenEvents().toLong()
    val n = 20
    val start = MouseInfo.getPointerInfo().location
    val dx = (x - start.x) / n.toDouble()
    val dy = (y - start.y) / n.toDouble()
    for (step in 1..n) {
      basicRobot.moveMouse(
        (start.x + dx * ((ln(1.0 * step / n) - ln(1.0 / n)) * n / (0 - ln(1.0 / n)))).toInt(),
        (start.y + dy * ((ln(1.0 * step / n) - ln(1.0 / n)) * n / (0 - ln(1.0 / n)))).toInt()
      )
      pause(pauseConstMs)
    }
    basicRobot.moveMouse(x, y)
  }

  private fun clickWithRetry(component: Component, where: Point?, mouseButton: MouseButton, counts: Int) {
    if (useInputEvents()) {
      postClickEvent(component, mouseButton, counts)
      return
    }
    //we don't want to register mouse listener to component that doesn't have mouse listeners
    //this will break event propagation to a parent component
    if (component.mouseListeners.isEmpty()) {
      moveMouseAndClick(component, where, mouseButton, counts)
      return
    }

    var attempt = 0
    while (attempt < 3) {
      val clickLatch = CountDownLatch(1)
      val mouseListener = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          clickLatch.countDown()
          logger.info("Mouse clicked on $component")
        }

        override fun mouseReleased(e: MouseEvent?) {
          clickLatch.countDown()
          logger.info("Mouse released on $component")
        }

        //on some components, mouse clicked/released are not registered on click
        override fun mousePressed(e: MouseEvent?) {
          clickLatch.countDown()
          logger.info("Mouse pressed on $component")
        }
      }

      component.addMouseListener(mouseListener)
      moveMouseAndClick(component, where, mouseButton, counts)
      val clicked = clickLatch.await(3, TimeUnit.SECONDS)
      component.removeMouseListener(mouseListener)

      if (clicked) {
        break
      }

      logger.warn("Repeating click. Click was unsuccessful on $component")
      attempt++
    }
  }

  private fun fastPressAndReleaseKey(keyCode: Int, vararg modifiers: Int) {
    val unifiedModifiers = unify(*modifiers)
    val updatedModifiers = Modifiers.updateModifierWithKeyCode(keyCode, unifiedModifiers)
    val modifiersKeys = Modifiers.keysFor(updatedModifiers)
    modifiersKeys.forEach { awtRobot.keyPress(it) }
    if (updatedModifiers == unifiedModifiers) {
      awtRobot.keyPress(keyCode)
      awtRobot.keyRelease(keyCode)
    }
    modifiersKeys.reversed().forEach { awtRobot.keyRelease(it) }
  }

  private fun makeScreenshot(screenshotArea: Rectangle): ByteArray {
    return ByteArrayOutputStream().use { b ->
      ImageIO.write(awtRobot.createScreenCapture(screenshotArea), "png", b)
      b.toByteArray()
    }
  }

  private fun moveMouseAndClick(where: Point, button: MouseButton, times: Int) {
    moveMouse(where.x, where.y)
    basicRobot.click(where, button, times)
  }

  private fun moveMouseAndClick(component: Component, where: Point?, button: MouseButton, times: Int) {
    if (where != null) {
      moveMouse(component, where)
      ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
      basicRobot.click(component, where, button, times)
    }
    else {
      moveMouse(component)
      ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
      basicRobot.click(component, button, times)
    }
  }

  private fun moveMouseWithAttempts(c: Component, x: Int, y: Int, attempts: Int = 3) {
    waitFor(Duration.ofSeconds(5), Duration.ofSeconds(1)) { performOnEdt { c.isShowing }!! }

    val componentLocation: Point = checkNotNull(performOnEdt { translate(c, x, y) })
    moveMouse(componentLocation.x, componentLocation.y)

    val componentLocationAfterMove: Point = checkNotNull(performOnEdt { translate(c, x, y) })
    val mouseLocation = MouseInfo.getPointerInfo().location
    if (mouseLocation.x != componentLocationAfterMove.x || mouseLocation.y != componentLocationAfterMove.y)
      moveMouseWithAttempts(c, x, y, attempts - 1)
  }

  private fun postClickEvent(component: Component, button: MouseButton = MouseButton.LEFT_BUTTON, clickCount: Int = 1, where: Point? = null) {
    val awtMouseButton = when (button) {
      MouseButton.LEFT_BUTTON -> MouseEvent.BUTTON1
      MouseButton.RIGHT_BUTTON -> MouseEvent.BUTTON3
      MouseButton.MIDDLE_BUTTON -> MouseEvent.BUTTON2
    }
    val eventQueue = IdeEventQueue.getInstance()

    val window = SwingUtilities.getWindowAncestor(component)
    val clickPoint = SwingUtilities.convertPoint(component, where ?: visibleCenterOf(component), window)
    repeat(clickCount) {
      val mousePressedEvent = MouseEvent(window, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                                         clickPoint.x, clickPoint.y, 1, awtMouseButton == MouseEvent.BUTTON3, awtMouseButton)
      eventQueue.postEvent(mousePressedEvent)
      val mouseReleasedEvent = MouseEvent(window, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                                          clickPoint.x, clickPoint.y, 1, false, awtMouseButton)
      eventQueue.postEvent(mouseReleasedEvent)
    }
  }

  companion object {
    private val logger = logger<SmoothRobot>()

    private fun useInputEvents(): Boolean = System.getProperty("driver.robot.use.input.events").toBoolean()

    private fun RemoteMouseButton.toAssertJ() = when (this) {
      RemoteMouseButton.LEFT -> MouseButton.LEFT_BUTTON
      RemoteMouseButton.MIDDLE -> MouseButton.MIDDLE_BUTTON
      RemoteMouseButton.RIGHT -> MouseButton.RIGHT_BUTTON
    }

    private fun unify(vararg modifiers: Int): Int = modifiers.reduceOrNull { acc, i -> acc or i } ?: 0

    private fun <T> performOnEdt(body: () -> T): T? =
      execute(object : GuiQuery<T>() {
        override fun executeInEDT() = body.invoke()
      })
  }
}