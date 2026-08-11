// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.robot

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.util.ui.StartupUiUtil
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Point

@ApiStatus.Internal
interface IdeRobot : Robot {
  fun hasInputFocus(): Boolean

  fun getColor(component: Component, point: Point? = null): Color

  fun makeScreenshot(): ByteArray

  fun dragAndDrop(fromComponent: Component, fromPoint: Point, toComponent: Component, toPoint: Point)

  fun selectAndDrag(component: Component, from: Point, to: Point, delayMs: Int)

  fun doubleKey(keyCode: Int)

  fun doublePressKeyAndHold(key: Int)

  fun tripleClick(c: Component)

  fun click(component: Component, mouseButton: RemoteMouseButton)

  fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int)

  fun click(c: Component, where: Point, button: RemoteMouseButton)

  fun click(c: Component, where: Point, button: RemoteMouseButton, times: Int)

  fun click(where: Point, button: RemoteMouseButton)

  fun click(where: Point, button: RemoteMouseButton, times: Int)

  fun strictClick(component: Component, point: Point? = null)

  fun pressMouse(mouseButton: RemoteMouseButton)

  fun pressMouse(component: Component, point: Point, mouseButton: RemoteMouseButton)

  fun pressMouse(point: Point, mouseButton: RemoteMouseButton)

  fun releaseMouse(mouseButton: RemoteMouseButton)

  fun moveMouseAndPress(component: Component, where: Point?)

  companion object {
    private fun useInputEvents(): Boolean =
      System.getProperty("driver.robot.use.input.events").toBoolean() || StartupUiUtil.isWaylandToolkit()

    fun create(
      basicRobot: Robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock(),
      awtRobot: java.awt.Robot = java.awt.Robot(),
    ): IdeRobot = if (useInputEvents()) InputEventsRobot(basicRobot, awtRobot) else SmoothRobot(basicRobot, awtRobot)
  }
}
