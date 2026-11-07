package com.intellij.ide.starter.driver.overlay

import com.intellij.driver.sdk.ui.StepOverlayInterceptor
import com.intellij.driver.sdk.ui.shownOverlay
import com.intellij.ide.starter.driver.FakeDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StepOverlayInterceptorTest {
  @Test
  fun `an IDE without the overlay turns off only its own overlays`() {
    val old = FakeDriver(hasOverlay = false)
    val new = FakeDriver()

    StepOverlayInterceptor().intercept("old IDE", old.proxy) {}
    // The overlay thread runs the calls in their order, so the read comes after the failed call of the old IDE.
    new.proxy.shownOverlay()
    StepOverlayInterceptor().intercept("old IDE again", old.proxy) {}
    StepOverlayInterceptor().intercept("new IDE", new.proxy) {}
    new.proxy.shownOverlay()

    assertEquals(listOf("show"), old.overlayCalls, "the old IDE gets no overlay call after the failed one")
    assertTrue("show" in new.overlayCalls, "the new IDE shows the overlay of its step")
  }
}
