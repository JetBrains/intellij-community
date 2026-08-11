// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.HwFacadeHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
internal class WebViewHwFacadeProviderTest {
  @Test
  fun providerIsAvailableByDefault() {
    assertTrue(WebViewHwFacadeProvider().isAvailable())
  }

  @Test
  @RegistryKey(key = WEBVIEW_HW_FACADE_REGISTRY_KEY, value = "false")
  fun providerIsUnavailableWhenRegistryFlagIsDisabled() {
    assertFalse(WebViewHwFacadeProvider().isAvailable())
  }

  @Test
  fun helperDelegatesWhenWebViewFacadeIsNotActive() {
    val delegate = RecordingHwFacadeHelper()
    val helper = WebViewHwFacadeHelper(JPanel(), delegate)
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    helper.addNotify()
    helper.show()
    helper.paint(image.graphics) {}
    helper.hide()
    helper.removeNotify()

    assertTrue(delegate.addNotifyCalled)
    assertTrue(delegate.showCalled)
    assertTrue(delegate.paintCalled)
    assertTrue(delegate.hideCalled)
    assertTrue(delegate.removeNotifyCalled)
  }

  @Test
  fun mousePressReleaseAndClickAreRedispatchedToNestedComponent() {
    val target = ScreenComponent(Rectangle(100, 100, 200, 100)).apply { layout = null }
    val child = ScreenComponent(Rectangle(110, 110, 40, 40))
    child.setBounds(10, 10, 40, 40)
    target.add(child)
    val recorder = RecordingMouseListener()
    child.addMouseListener(recorder)
    val redispatcher = WebViewMouseEventRedispatcher(target)

    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_PRESSED, 120, 120))
    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_RELEASED, 120, 120))
    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_CLICKED, 120, 120))

    assertEquals(listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), recorder.events)
  }

  @Test
  fun mouseReleaseAfterPressIsRedispatchedToPressedComponent() {
    val target = ScreenComponent(Rectangle(100, 100, 200, 100)).apply { layout = null }
    val pressedChild = ScreenComponent(Rectangle(110, 110, 40, 40))
    pressedChild.setBounds(10, 10, 40, 40)
    val releasedOverChild = ScreenComponent(Rectangle(160, 110, 40, 40))
    releasedOverChild.setBounds(60, 10, 40, 40)
    target.add(pressedChild)
    target.add(releasedOverChild)
    val pressedRecorder = RecordingMouseListener()
    val releasedOverRecorder = RecordingMouseListener()
    pressedChild.addMouseListener(pressedRecorder)
    releasedOverChild.addMouseListener(releasedOverRecorder)
    val redispatcher = WebViewMouseEventRedispatcher(target)

    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_PRESSED, 120, 120))
    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_RELEASED, 170, 120))

    assertTrue(pressedRecorder.events.contains(MouseEvent.MOUSE_RELEASED))
    assertFalse(releasedOverRecorder.events.contains(MouseEvent.MOUSE_RELEASED))
  }

  @Test
  fun disabledDeepestComponentFallsBackToEnabledParent() {
    val target = ScreenComponent(Rectangle(100, 100, 200, 100)).apply { layout = null }
    val parent = ScreenComponent(Rectangle(110, 110, 80, 40)).apply { layout = null }
    parent.setBounds(10, 10, 80, 40)
    val disabledChild = ScreenComponent(Rectangle(120, 120, 30, 20)).apply { isEnabled = false }
    disabledChild.setBounds(10, 10, 30, 20)
    parent.add(disabledChild)
    target.add(parent)
    val parentRecorder = RecordingMouseListener()
    val disabledChildRecorder = RecordingMouseListener()
    parent.addMouseListener(parentRecorder)
    disabledChild.addMouseListener(disabledChildRecorder)
    val redispatcher = WebViewMouseEventRedispatcher(target)

    redispatcher.redispatch(mouseEvent(target, MouseEvent.MOUSE_CLICKED, 125, 125))

    assertEquals(listOf(MouseEvent.MOUSE_CLICKED), parentRecorder.events)
    assertTrue(disabledChildRecorder.events.isEmpty())
  }

  private fun mouseEvent(source: JComponent, id: Int, screenX: Int, screenY: Int): MouseEvent {
    val local = Point(screenX, screenY)
    local.translate(-source.locationOnScreen.x, -source.locationOnScreen.y)
    return MouseEvent(source, id, System.currentTimeMillis(), 0, local.x, local.y, screenX, screenY, 1, false, MouseEvent.BUTTON1)
  }

  private class RecordingMouseListener : MouseAdapter() {
    val events = ArrayList<Int>()

    override fun mousePressed(e: MouseEvent) {
      events += e.id
    }

    override fun mouseReleased(e: MouseEvent) {
      events += e.id
    }

    override fun mouseClicked(e: MouseEvent) {
      events += e.id
    }
  }

  private class ScreenComponent(screenBounds: Rectangle) : JPanel() {
    private val screenLocation = screenBounds.location

    init {
      size = Dimension(screenBounds.width, screenBounds.height)
    }

    override fun isShowing(): Boolean = true

    override fun getLocationOnScreen(): Point = screenLocation
  }

  private class RecordingHwFacadeHelper : HwFacadeHelper() {
    var addNotifyCalled = false
      private set
    var removeNotifyCalled = false
      private set
    var showCalled = false
      private set
    var hideCalled = false
      private set
    var paintCalled = false
      private set

    override fun addNotify() {
      addNotifyCalled = true
    }

    override fun removeNotify() {
      removeNotifyCalled = true
    }

    override fun show() {
      showCalled = true
    }

    override fun hide() {
      hideCalled = true
    }

    override fun paint(g: Graphics, targetPaint: Consumer<in Graphics>) {
      paintCalled = true
      targetPaint.accept(g)
    }
  }
}
