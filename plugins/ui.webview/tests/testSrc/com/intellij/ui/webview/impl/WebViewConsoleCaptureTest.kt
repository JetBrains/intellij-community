// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class WebViewConsoleCaptureTest {

  @Test
  fun formatMessageUsesReadableJsTimestampOnly() {
    val timestamp = 1_782_995_696_789L
    val message = WebViewConsoleCapture().formatMessage(
      WebViewConsolePayload(
        method = "warn",
        jsTimeEpochMs = timestamp,
        args = listOf("hello", "world"),
      ),
    )

    assertEquals("[js=${Instant.ofEpochMilli(timestamp)}] hello world", message)
  }

  @Test
  fun loggerCategoryContainsViewIdOnlyWhenPresent() {
    val capture = WebViewConsoleCapture()
    capture.setViewId("main/view 42")

    assertEquals("#com.intellij.ui.webview.console.main_view_42", capture.loggerCategory())
    capture.setViewId(null)
    assertEquals("#com.intellij.ui.webview.console", capture.loggerCategory())
  }

  @Test
  fun loggerCategoryUsesConfiguredBaseCategory() {
    val capture = WebViewConsoleCapture("#custom.webview.console")
    capture.setViewId("main/view 42")

    assertEquals("#custom.webview.console.main_view_42", capture.loggerCategory())
  }
}
