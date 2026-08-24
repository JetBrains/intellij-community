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

  /**
   * Enqueues a click **addressed to [component]** and returns: `times` x (`MOUSE_PRESSED`, `MOUSE_RELEASED`,
   * `MOUSE_CLICKED`), sourced at [component] itself, at coordinates relative to it ([where] `== null` — its centre).
   * No mouse move precedes them, and nothing waits for them.
   *
   * This is the gesture for a click whose effect cannot be awaited from the caller — the classic case being a click
   * that opens a modal dialog, which blocks the dispatching thread for as long as the dialog is up, so any robot
   * call that waits for its own event to be dispatched deadlocks instead of returning. Test frameworks reach for a
   * hand-rolled `postEvent(MouseEvent(...))` at exactly this point; this is that primitive, shared.
   *
   * Addressing is the difference from the ordinary [click] family. A [click] describes *where on the screen* the
   * gesture happens and lets AWT decide who receives it, so a balloon or popup that appears in between silently
   * takes it. An addressed post names the recipient, and dispatch to a named source cannot be stolen. It also
   * needs no input backend: no native pointer, no window-sourced synthesis, so every [IdeRobot] behaves the same.
   *
   * Only [component]`.isShowing` and `isEnabled` are checked, on the EDT, before the events are queued. Everything
   * else is explicitly **not** guaranteed:
   * - **No delivery observation.** Dispatch happens after this call returns. Nothing here reports whether
   *   [component] received the gesture, acted on it, or was disposed first.
   * - **The aim can go stale.** [where] is resolved before posting; a component that moves, resizes, or hides
   *   before dispatch is clicked at a point that no longer means what it meant.
   * - **A post into a modally blocked window is dropped silently.** The event queue filters it by modality state
   *   and no failure surfaces.
   * - **No ordering against later driver calls.** Post an explicit barrier if a subsequent step depends on this
   *   gesture having been dispatched (`IdeEventQueue.flushQueue`, exposed to the driver SDK).
   *
   * Delivery semantics that observe the click, retry, or route the gesture natively are tracked separately
   * in AT-5091.
   */
  fun postGesture(component: Component, where: Point? = null, button: RemoteMouseButton = RemoteMouseButton.LEFT, times: Int = 1)

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
