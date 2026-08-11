// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.jbr.JdkEx
import com.intellij.ui.HwFacadeHelper
import com.intellij.ui.HwFacadeProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.MouseEventAdapter
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.IllegalComponentStateException
import java.awt.Point
import java.awt.Rectangle
import java.awt.Transparency
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.VolatileImage
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities

internal const val WEBVIEW_HW_FACADE_REGISTRY_KEY = "ide.webview.heavyweight.hwfacade.enabled"

private val HW_FACADE_EP_NAME = ExtensionPointName.create<HwFacadeProvider>("com.intellij.hwFacadeProvider")

@ApiStatus.Internal
class WebViewHwFacadeProvider : HwFacadeProvider {
  override fun isAvailable(): Boolean {
    return Registry.`is`(WEBVIEW_HW_FACADE_REGISTRY_KEY, true)
  }

  override fun create(target: JComponent): HwFacadeHelper {
    return WebViewHwFacadeHelper(target, createDelegate(target))
  }

  private fun createDelegate(target: JComponent): HwFacadeHelper {
    val provider = HW_FACADE_EP_NAME.findFirstSafe { it !== this && it !is WebViewHwFacadeProvider && it.isAvailable() }
    return provider?.create(target) ?: NoOpHwFacadeHelper
  }
}

internal class WebViewHwFacadeHelper(
  private val target: JComponent,
  private val delegate: HwFacadeHelper,
) : HwFacadeHelper() {
  private var facadeWindow: JWindow? = null
  private var backBuffer: VolatileImage? = null
  private var owner: Window? = null
  private var ownerListener: ComponentAdapter? = null
  private var targetListener: ComponentAdapter? = null
  private val mouseEventRedispatcher = WebViewMouseEventRedispatcher(target)

  private val registryListener = Runnable {
    if (target.isShowing) {
      activateIfNeeded()
    }
  }

  @RequiresEdt
  override fun addNotify() {
    delegate.addNotify()
    WebViewHeavyweightHostRegistry.addChangeListener(registryListener)
    installTargetListener()
    if (target.isVisible) {
      activateIfNeeded()
    }
  }

  @RequiresEdt
  override fun removeNotify() {
    WebViewHeavyweightHostRegistry.removeChangeListener(registryListener)
    uninstallTargetListener()
    disposeFacade()
    delegate.removeNotify()
  }

  @RequiresEdt
  override fun show() {
    delegate.show()
    if (!isEnabled()) {
      disposeFacade()
      return
    }

    if (isActive()) {
      facadeWindow?.isVisible = true
      delegate.hide()
    }
    else {
      activateIfNeeded()
    }
  }

  @RequiresEdt
  override fun hide() {
    facadeWindow?.isVisible = false
    delegate.hide()
  }

  @RequiresEdt
  override fun paint(g: Graphics, targetPaint: Consumer<in Graphics>) {
    if (!isEnabled()) {
      disposeFacade()
      delegate.paint(g, targetPaint)
      return
    }

    val window = facadeWindow
    if (window == null) {
      delegate.paint(g, targetPaint)
      return
    }

    val width = target.width
    val height = target.height
    if (width <= 0 || height <= 0) return

    val image = ensureBackBuffer(width, height)
    val bbGraphics = image.graphics as Graphics2D
    try {
      bbGraphics.composite = AlphaComposite.Clear
      bbGraphics.fillRect(0, 0, width, height)
      bbGraphics.composite = AlphaComposite.SrcOver
      targetPaint.accept(bbGraphics)
    }
    finally {
      bbGraphics.dispose()
    }
    window.repaint()
  }

  @RequiresEdt
  private fun activateIfNeeded() {
    if (isActive() || !isEnabled() || !target.isShowing) return
    val targetBounds = getTargetBoundsOnScreen() ?: return
    if (!WebViewHeavyweightHostRegistry.hasOverlappingHost(targetBounds, target)) return

    val owner = SwingUtilities.getWindowAncestor(target) ?: return
    this.owner = owner
    val ownerListener = object : ComponentAdapter() {
      @RequiresEdt
      override fun componentMoved(e: ComponentEvent) {
        updateFacadeBounds()
      }

      @RequiresEdt
      override fun componentResized(e: ComponentEvent) {
        updateFacadeBounds()
      }
    }
    this.ownerListener = ownerListener
    owner.addComponentListener(ownerListener)

    val window = JWindow(owner)
    facadeWindow = window
    val facadePanel = object : JPanel() {
      init {
        isOpaque = false
        isDoubleBuffered = false
      }

      @RequiresEdt
      override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
          g2.composite = AlphaComposite.Clear
          g2.fillRect(0, 0, width, height)
          g2.composite = AlphaComposite.SrcOver
          backBuffer?.let { g2.drawImage(it, 0, 0, null) }
        }
        finally {
          g2.dispose()
        }
      }
    }
    facadePanel.addMouseListener(mouseEventRedispatcher.eventDispatcher)
    facadePanel.addMouseMotionListener(mouseEventRedispatcher.eventDispatcher)
    facadePanel.addMouseWheelListener(mouseEventRedispatcher.eventDispatcher)
    window.add(facadePanel)
    window.type = Window.Type.POPUP
    window.rootPane.putClientProperty("Window.shadow", false)
    window.bounds = targetBounds
    window.isAutoRequestFocus = false
    window.isFocusable = false
    window.focusableWindowState = false
    JdkEx.setTransparent(window)
    window.isVisible = true
    delegate.hide()
  }

  @RequiresEdt
  private fun installTargetListener() {
    if (targetListener != null) return
    val listener = object : ComponentAdapter() {
      @RequiresEdt
      override fun componentResized(e: ComponentEvent) {
        updateFacadeBoundsOrActivate()
      }

      @RequiresEdt
      override fun componentMoved(e: ComponentEvent) {
        updateFacadeBoundsOrActivate()
      }

      @RequiresEdt
      override fun componentShown(e: ComponentEvent) {
        activateIfNeeded()
      }

      @RequiresEdt
      override fun componentHidden(e: ComponentEvent) {
        hide()
      }
    }
    targetListener = listener
    target.addComponentListener(listener)
  }

  @RequiresEdt
  private fun uninstallTargetListener() {
    targetListener?.let {
      target.removeComponentListener(it)
      targetListener = null
    }
  }

  @RequiresEdt
  private fun updateFacadeBoundsOrActivate() {
    if (isActive()) {
      updateFacadeBounds()
    }
    else {
      activateIfNeeded()
    }
  }

  @RequiresEdt
  private fun updateFacadeBounds() {
    val window = facadeWindow ?: return
    if (!target.isVisible) return
    val targetBounds = getTargetBoundsOnScreen() ?: return
    window.bounds = targetBounds
  }

  @RequiresEdt
  private fun ensureBackBuffer(width: Int, height: Int): VolatileImage {
    val image = backBuffer
    if (image != null && image.width == width && image.height == height) {
      return image
    }

    image?.flush()
    return GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .defaultScreenDevice
      .defaultConfiguration
      .createCompatibleVolatileImage(width, height, Transparency.TRANSLUCENT)
      .also { backBuffer = it }
  }

  @RequiresEdt
  private fun disposeFacade() {
    mouseEventRedispatcher.reset()
    facadeWindow?.dispose()
    facadeWindow = null
    backBuffer?.flush()
    backBuffer = null
    val owner = owner
    val ownerListener = ownerListener
    if (owner != null && ownerListener != null) {
      owner.removeComponentListener(ownerListener)
    }
    this.owner = null
    this.ownerListener = null
  }

  @RequiresEdt
  private fun isActive(): Boolean {
    return facadeWindow != null && isEnabled()
  }

  private fun isEnabled(): Boolean {
    return Registry.`is`(WEBVIEW_HW_FACADE_REGISTRY_KEY, true)
  }

  private fun getTargetBoundsOnScreen(): Rectangle? {
    return try {
      Rectangle(target.locationOnScreen, target.size)
    }
    catch (_: IllegalComponentStateException) {
      null
    }
  }
}

internal class WebViewMouseEventRedispatcher(
  private val target: JComponent,
) {
  private var currentTarget: Component? = null
  private var pressedTarget: Component? = null

  val eventDispatcher: MouseAdapter = object : MouseAdapter() {
    @RequiresEdt
    override fun mouseClicked(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mousePressed(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseReleased(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseEntered(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseMoved(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseDragged(e: MouseEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseWheelMoved(e: MouseWheelEvent) = redispatch(e)

    @RequiresEdt
    override fun mouseExited(e: MouseEvent) {
      clearCurrentTarget(e)
      pressedTarget = null
      e.consume()
    }
  }

  @RequiresEdt
  fun reset() {
    currentTarget = null
    pressedTarget = null
  }

  @RequiresEdt
  internal fun redispatch(event: MouseEvent) {
    val targetUnderPointer = findEnabledTarget(event)
    val dispatchTarget = selectDispatchTarget(event, targetUnderPointer)
    if (dispatchTarget == null) {
      clearCurrentTarget(event)
      if (event.id == MouseEvent.MOUSE_RELEASED) pressedTarget = null
      event.consume()
      return
    }

    updateCurrentTarget(event, targetUnderPointer)
    if (event.id == MouseEvent.MOUSE_PRESSED) pressedTarget = dispatchTarget
    val dispatchPoint = getEventPoint(event, dispatchTarget)
    if (dispatchPoint == null) {
      if (event.id == MouseEvent.MOUSE_RELEASED) pressedTarget = null
      event.consume()
      return
    }
    try {
      MouseEventAdapter.redispatch(event, dispatchTarget, dispatchPoint.x, dispatchPoint.y)
    }
    finally {
      if (event.id == MouseEvent.MOUSE_RELEASED) pressedTarget = null
      event.consume()
    }
  }

  @RequiresEdt
  private fun selectDispatchTarget(event: MouseEvent, targetUnderPointer: Component?): Component? {
    if (event.id == MouseEvent.MOUSE_DRAGGED || event.id == MouseEvent.MOUSE_RELEASED) {
      pressedTarget?.takeIf(::canDispatchTo)?.let { return it }
    }
    return targetUnderPointer?.takeIf(::canDispatchTo)
  }

  @RequiresEdt
  private fun findEnabledTarget(event: MouseEvent): Component? {
    if (!target.isShowing || !target.isEnabled) return null

    val point = getEventPoint(event, target) ?: return null
    if (!target.contains(point)) return null

    var component: Component? = SwingUtilities.getDeepestComponentAt(target, point.x, point.y) ?: target
    while (component != null && !component.isEnabled) {
      component = component.parent
    }
    return component
  }

  @RequiresEdt
  private fun updateCurrentTarget(event: MouseEvent, targetUnderPointer: Component?) {
    if (event.id == MouseEvent.MOUSE_ENTERED) {
      val previousTarget = currentTarget
      if (previousTarget != null && previousTarget !== targetUnderPointer) {
        dispatchSyntheticMouseEvent(event, previousTarget, MouseEvent.MOUSE_EXITED)
      }
      currentTarget = targetUnderPointer
      return
    }

    val newTarget = targetUnderPointer ?: return
    if (currentTarget === newTarget) return
    clearCurrentTarget(event)
    currentTarget = newTarget
    dispatchSyntheticMouseEvent(event, newTarget, MouseEvent.MOUSE_ENTERED)
  }

  @RequiresEdt
  private fun clearCurrentTarget(event: MouseEvent?) {
    val previousTarget = currentTarget ?: return
    currentTarget = null
    if (event != null) {
      dispatchSyntheticMouseEvent(event, previousTarget, MouseEvent.MOUSE_EXITED)
    }
  }

  @RequiresEdt
  private fun dispatchSyntheticMouseEvent(event: MouseEvent, dispatchTarget: Component, eventId: Int) {
    if (!canDispatchTo(dispatchTarget)) return
    val point = getEventPoint(event, dispatchTarget) ?: return
    dispatchTarget.dispatchEvent(
      MouseEventAdapter.convert(event, dispatchTarget, eventId, event.`when`, event.modifiersEx, point.x, point.y)
    )
  }

  @RequiresEdt
  private fun getEventPoint(event: MouseEvent, component: Component): Point? {
    return try {
      val point = event.locationOnScreen
      val componentLocation = component.locationOnScreen
      point.translate(-componentLocation.x, -componentLocation.y)
      point
    }
    catch (_: IllegalComponentStateException) {
      null
    }
  }

  @RequiresEdt
  private fun canDispatchTo(component: Component): Boolean {
    return component.isShowing && component.isEnabled &&
           (component === target || SwingUtilities.isDescendingFrom(component, target))
  }
}

private object NoOpHwFacadeHelper : HwFacadeHelper() {
  override fun addNotify() {
  }

  override fun removeNotify() {
  }

  override fun show() {
  }

  override fun hide() {
  }

  override fun paint(g: Graphics, targetPaint: Consumer<in Graphics>) {
    targetPaint.accept(g)
  }
}
