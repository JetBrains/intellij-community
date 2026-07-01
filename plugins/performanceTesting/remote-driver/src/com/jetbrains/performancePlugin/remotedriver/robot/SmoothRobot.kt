// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.performancePlugin.remotedriver.waitFor
import org.assertj.swing.awt.AWT.translate
import org.assertj.swing.awt.AWT.visibleCenterOf
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.core.Scrolling.scrollToVisible
import org.assertj.swing.timing.Pause.pause
import java.awt.AWTEvent
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.ln

internal class SmoothRobot(
  basicRobot: Robot,
  awtRobot: java.awt.Robot,
) : AbstractIdeRobot(basicRobot, awtRobot) {

  init {
    settings().apply {
      delayBetweenEvents(10)
      simpleWaitForIdle(true)
      timeoutToFindPopup(1000)
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(KeyLoggerAWTEventListener, AWTEvent.KEY_EVENT_MASK)
  }

  override fun doubleKey(keyCode: Int) {
    awtRobot.keyPress(keyCode)
    awtRobot.keyRelease(keyCode)
    Thread.sleep(10)
    awtRobot.keyPress(keyCode)
    awtRobot.keyRelease(keyCode)
  }

  override fun doublePressKeyAndHold(key: Int) {
    awtRobot.keyPress(key)
    awtRobot.keyRelease(key)
    Thread.sleep(10)
    awtRobot.keyPress(key)
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

  override fun click(where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(where, button, times)
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

  override fun dragAndDrop(fromComponent: Component, fromPoint: Point, toComponent: Component, toPoint: Point) {
    moveMouse(fromComponent, fromPoint)
    try {
      pause(300)
      basicRobot.pressMouse(fromComponent, fromPoint, MouseButton.LEFT_BUTTON)
      pause(500)
      moveMouse(toComponent, toPoint)
      pause(500)
    }
    finally {
      basicRobot.releaseMouse(MouseButton.LEFT_BUTTON)
    }
  }

  override fun cleanUp() {
    basicRobot.cleanUp()
    Toolkit.getDefaultToolkit().removeAWTEventListener(KeyLoggerAWTEventListener)
  }

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

  override fun doClick(component: Component, where: Point?, mouseButton: MouseButton, clickCount: Int) {
    val clickLatch = CountDownLatch(1)

    val listener = MouseClickAWTEventListener(component, clickCount, mouseButton) { clickLatch.countDown() }

    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
    try {
      moveMouseAndClick(component, where, mouseButton, clickCount)

      val clicked = clickLatch.await(3, TimeUnit.SECONDS) || listener.mousePressedEventReceived().also { // there are cases when a mouse event is handled by a custom dispatcher of IdeEventQueue, and we do not receive the MOUSE_RELEASED event
        logger.info("The MOUSE_RELEASED event was missing, or MOUSE_PRESSED or MOUSE_RELEASED events were received on a different component, but were expected on $component")
      }
      if (!clicked) {
        logger.warn("Click was unsuccessful on $component")
      }
    }
    finally {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }
  }

  /**
   * Performs a strict click in the context of SmoothRobot by repeating the full
   * mouse cycle until a `mouseClicked` event is observed.
   *
   * Why this is needed: some components trigger their action only when the release happens
   * within the same target. If we stop on `pressed` or `released` alone, the intended action may not
   * happen.
   *
   * !!! Don't use strictClick if the component should disappear after mouse release.
   */

  override fun strictClick(component: Component, point: Point?) {
    strictClickWithRetry(component, point)
  }

  private fun strictClickWithRetry(component: Component, where: Point?) {
    //we don't want to register mouse listener to component that doesn't have mouse listeners
    //this will break event propagation to a parent component
    if (component.mouseListeners.isEmpty()) {
      moveMouseAndClick(component, where, MouseButton.LEFT_BUTTON, 1)
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
      }

      component.addMouseListener(mouseListener)
      moveMouseAndClick(component, where, MouseButton.LEFT_BUTTON, 1)
      val clicked = clickLatch.await(3, TimeUnit.SECONDS)
      component.removeMouseListener(mouseListener)

      if (clicked) {
        break
      }

      logger.warn("Repeating click. Click was unsuccessful on $component")
      attempt++
    }
  }

  override fun doPressAndReleaseKey(keyCode: Int, modifiers: Int) {
    basicRobot.pressAndReleaseKey(keyCode, modifiers)
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

  private class MouseClickAWTEventListener(
    private val componentToBeClicked: Component,
    private val clickCount: Int,
    private val mouseButton: MouseButton,
    private val onMouseClicked: () -> Unit,
  ) : AWTEventListener {
    private var pressedAtComponent: Component? = null

    override fun eventDispatched(event: AWTEvent) {
      if (event !is MouseEvent) return
      if (event.id == MouseEvent.MOUSE_PRESSED) {
        pressedAtComponent = event.component
      }
      else if (event.id == MouseEvent.MOUSE_RELEASED || event.id == MouseEvent.MOUSE_CLICKED) { // in some cases we don't receive the MOUSE_CLICKED event (for example, if the component disappears after the MOUSE_RELEASED event)
        if (event.clickCount == clickCount && event.button == mouseButton.awtButton && event.component === pressedAtComponent
            && isAncestorOrDescendant(pressedAtComponent, componentToBeClicked)) { // in some cases a click is handled by a child or parent component
          onMouseClicked()
        }
        pressedAtComponent = null
      }
    }

    fun mousePressedEventReceived(): Boolean {
      return isAncestorOrDescendant(pressedAtComponent, componentToBeClicked)
    }

    private fun isAncestorOrDescendant(c1: Component?, c2: Component): Boolean {
      return c1 != null && (SwingUtilities.isDescendingFrom(c1, c2) || SwingUtilities.isDescendingFrom(c2, c1))
    }
  }

  private object KeyLoggerAWTEventListener : AWTEventListener {
    override fun eventDispatched(event: AWTEvent) {
      if (event !is KeyEvent) return
      when (event.id) {
        KeyEvent.KEY_PRESSED -> logger.info("KEY_PRESSED: ${KeyEvent.getKeyText(event.keyCode)} on ${event.component}")
        KeyEvent.KEY_RELEASED -> logger.info("KEY_RELEASED: ${KeyEvent.getKeyText(event.keyCode)} on ${event.component}")
      }
    }
  }

  companion object {
    private val logger = logger<SmoothRobot>()
  }
}