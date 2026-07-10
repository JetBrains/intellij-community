// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

internal class WebViewHeavyweightHostRegistryTest {
  @Test
  fun registeredHostOverlapsTargetBounds() {
    val host = ScreenComponent(Rectangle(10, 10, 100, 100))
    val target = ScreenComponent(Rectangle(50, 50, 20, 20))
    val registration = WebViewHeavyweightHostRegistry.register(host)
    try {
      assertTrue(WebViewHeavyweightHostRegistry.hasOverlappingHost(Rectangle(target.locationOnScreen, target.size), target))
    }
    finally {
      Disposer.dispose(registration)
    }

    assertFalse(WebViewHeavyweightHostRegistry.hasOverlappingHost(Rectangle(target.locationOnScreen, target.size), target))
  }

  @Test
  fun nonOverlappingHiddenNonShowingAndZeroSizeHostsAreIgnored() {
    val target = ScreenComponent(Rectangle(50, 50, 20, 20))
    assertHostIgnored(ScreenComponent(Rectangle(200, 200, 30, 30)), target)
    assertHostIgnored(ScreenComponent(Rectangle(10, 10, 100, 100)).apply { isVisible = false }, target)
    assertHostIgnored(ScreenComponent(Rectangle(10, 10, 100, 100)).apply { showing = false }, target)
    assertHostIgnored(ScreenComponent(Rectangle(10, 10, 0, 100)), target)
  }

  @Test
  fun descendantHostIsIgnored() {
    val target = ScreenComponent(Rectangle(10, 10, 120, 120)).apply {
      layout = null
    }
    val host = ScreenComponent(Rectangle(20, 20, 40, 40))
    target.add(host)
    val registration = WebViewHeavyweightHostRegistry.register(host)
    try {
      assertFalse(WebViewHeavyweightHostRegistry.hasOverlappingHost(Rectangle(target.locationOnScreen, target.size), target))
    }
    finally {
      Disposer.dispose(registration)
    }
  }

  @Test
  fun listenersAreNotifiedOnRegisterChangeAndUnregister() {
    val host = ScreenComponent(Rectangle(10, 10, 100, 100))
    var notifications = 0
    val listener = Runnable { notifications++ }

    WebViewHeavyweightHostRegistry.addChangeListener(listener)
    val registration = WebViewHeavyweightHostRegistry.register(host)
    try {
      assertEquals(1, notifications)

      WebViewHeavyweightHostRegistry.componentChanged(host)
      assertEquals(2, notifications)
    }
    finally {
      Disposer.dispose(registration)
      WebViewHeavyweightHostRegistry.removeChangeListener(listener)
    }

    assertEquals(3, notifications)
  }

  private fun assertHostIgnored(host: ScreenComponent, target: ScreenComponent) {
    val registration = WebViewHeavyweightHostRegistry.register(host)
    try {
      assertFalse(WebViewHeavyweightHostRegistry.hasOverlappingHost(Rectangle(target.locationOnScreen, target.size), target))
    }
    finally {
      Disposer.dispose(registration)
    }
  }

  private class ScreenComponent(screenBounds: Rectangle) : JComponent() {
    var showing = true
    private var screenLocation = screenBounds.location

    init {
      size = Dimension(screenBounds.width, screenBounds.height)
    }

    override fun isShowing(): Boolean = showing

    override fun getLocationOnScreen(): Point = screenLocation
  }
}
