// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.impl.openWebViewPopupUrlExternally
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WebViewPopupPolicyTest {
  @Test
  fun opensOnlyExternalHttpUrls() {
    val openedUrls = mutableListOf<String>()

    assertTrue(openWebViewPopupUrlExternally("https://www.jetbrains.com/help", openedUrls::add))
    assertTrue(openWebViewPopupUrlExternally("  http://example.com/path  ", openedUrls::add))

    assertEquals(listOf("https://www.jetbrains.com/help", "http://example.com/path"), openedUrls)
  }

  @Test
  fun blocksInternalAndUnsupportedUrls() {
    val openedUrls = mutableListOf<String>()
    val blockedUrls = listOf(
      "",
      "about:blank",
      "javascript:alert(1)",
      "data:text/html,hello",
      "file:///tmp/index.html",
      "https://ij-webview-assets.local/index.html",
      "ij-webview-asset:/index.html",
      "https:relative",
      "not a url",
    )

    for (url in blockedUrls) {
      assertFalse(openWebViewPopupUrlExternally(url, openedUrls::add), url)
    }
    assertEquals(emptyList<String>(), openedUrls)
  }
}
