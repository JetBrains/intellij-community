// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import org.assertj.swing.awt.AWT.translate
import org.assertj.swing.awt.AWT.visibleCenterOf
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner.execute
import org.assertj.swing.edt.GuiQuery
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal abstract class AbstractIdeRobot(
  protected val basicRobot: Robot,
  protected val awtRobot: java.awt.Robot,
): IdeRobot, Robot by basicRobot {

  protected abstract fun doClick(component: Component, where: Point?, mouseButton: MouseButton, clickCount: Int)

  protected abstract fun doPressAndReleaseKey(keyCode: Int, modifiers: Int)

  @Suppress("TestOnlyProblems")
  override fun hasInputFocus(): Boolean {
    val eventQueue = IdeEventQueue.getInstance()
    val keyEventsPostedBefore = eventQueue.keyboardEventPosted.get()
    pressAndReleaseKey(KeyEvent.VK_F13) // let's hope there is no action assigned to F13
    return keyEventsPostedBefore < eventQueue.keyboardEventPosted.get()
  }

  override fun getColor(component: Component, point: Point?): Color {
    val where = point ?: visibleCenterOf(component)
    val translatedPoint = performOnEdt { translate(component, where.x, where.y) }
    checkNotNull(translatedPoint) { "Translated point should be not null" }
    return awtRobot.getPixelColor(translatedPoint.x, translatedPoint.y)
  }

  override fun click(component: Component, mouseButton: RemoteMouseButton) {
    click(component, mouseButton.toMouseButton())
  }

  override fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int) {
    click(component, mouseButton.toMouseButton(), counts)
  }

  override fun click(c: Component, where: Point, button: RemoteMouseButton) {
    click(c, where, button.toMouseButton(), 1)
  }

  override fun click(c: Component, where: Point, button: RemoteMouseButton, times: Int) {
    click(c, where, button.toMouseButton(), times)
  }

  override fun click(where: Point, button: RemoteMouseButton) {
    click(where, button.toMouseButton(), 1)
  }

  override fun click(where: Point, button: RemoteMouseButton, times: Int) {
    click(where, button.toMouseButton(), times)
  }

  override fun click(component: Component) {
    doClick(component, null, MouseButton.LEFT_BUTTON, 1)
  }

  override fun click(component: Component, mouseButton: MouseButton) {
    doClick(component, null, mouseButton, 1)
  }

  override fun click(component: Component, point: Point) {
    doClick(component, point, MouseButton.LEFT_BUTTON, 1)
  }

  override fun click(component: Component, mouseButton: MouseButton, counts: Int) {
    doClick(component, null, mouseButton, counts)
  }

  override fun click(c: Component, where: Point, button: MouseButton, times: Int) {
    doClick(c, where, button, times)
  }

  override fun doubleClick(c: Component) {
    doClick(c, null, MouseButton.LEFT_BUTTON, 2)
  }

  override fun rightClick(component: Component) {
    doClick(component, null, MouseButton.RIGHT_BUTTON, 1)
  }

  override fun tripleClick(c: Component) {
    click(c, MouseButton.LEFT_BUTTON, 3)
  }

  override fun pressMouse(mouseButton: RemoteMouseButton) {
    pressMouse(mouseButton.toMouseButton())
  }

  override fun pressMouse(component: Component, point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(component, point, mouseButton.toMouseButton())
  }

  override fun pressMouse(point: Point, mouseButton: RemoteMouseButton) {
    pressMouse(point, mouseButton.toMouseButton())
  }

  override fun releaseMouse(mouseButton: RemoteMouseButton) {
    releaseMouse(mouseButton.toMouseButton())
  }

  override fun moveMouseAndPress(component: Component, where: Point?) {
    if (where != null) {
      moveMouse(component, where)
    }
    else {
      moveMouse(component)
    }
    ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
    pressMouse(MouseButton.LEFT_BUTTON)
  }

  override fun pressAndReleaseKey(keyCode: Int, vararg modifiers: Int) {
    doPressAndReleaseKey(keyCode, unifyModifiers(*modifiers))
  }

  override fun doubleKey(keyCode: Int) {
    doPressAndReleaseKey(keyCode, 0)
    doPressAndReleaseKey(keyCode, 0)
  }

  override fun doublePressKeyAndHold(key: Int) {
    doPressAndReleaseKey(key, 0)
    pressKey(key)
  }

  override fun selectAndDrag(component: Component, from: Point, to: Point, delayMs: Int) {
    moveMouse(component, from)

    try {
      click(component, from)
      pressMouse(RemoteMouseButton.LEFT)

      Thread.sleep(delayMs.toLong())
      moveMouse(component, to)

      Thread.sleep(delayMs.toLong())
      moveMouse(component, to)
    } finally {
      releaseMouse(RemoteMouseButton.LEFT)
    }
  }

  override fun makeScreenshot(): ByteArray {
    val area = Rectangle(Toolkit.getDefaultToolkit().screenSize)
    return ByteArrayOutputStream().use { b ->
      ImageIO.write(awtRobot.createScreenCapture(area), "png", b)
      b.toByteArray()
    }
  }

  override fun cleanUp() = Unit

  protected fun unifyModifiers(vararg modifiers: Int): Int = modifiers.reduceOrNull { acc, i -> acc or i } ?: 0

  protected fun <T> performOnEdt(body: () -> T): T? =
    execute(object : GuiQuery<T>() {
      override fun executeInEDT() = body.invoke()
    })

  protected companion object {
    val MouseButton.awtButton
      get() = when (this) {
        MouseButton.LEFT_BUTTON -> MouseEvent.BUTTON1
        MouseButton.MIDDLE_BUTTON -> MouseEvent.BUTTON2
        MouseButton.RIGHT_BUTTON -> MouseEvent.BUTTON3
      }

    private fun RemoteMouseButton.toMouseButton(): MouseButton = when (this) {
      RemoteMouseButton.LEFT -> MouseButton.LEFT_BUTTON
      RemoteMouseButton.MIDDLE -> MouseButton.MIDDLE_BUTTON
      RemoteMouseButton.RIGHT -> MouseButton.RIGHT_BUTTON
    }
  }
}
