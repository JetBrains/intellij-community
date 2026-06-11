// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.ui.webview.api.WebViewAssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class WebViewAssetPathTest {
  @Test
  fun normalizesRelativePath() {
    assertEquals("webview/views/sample-panel/index.html", WebViewAssetPath.of("webview/views/sample-panel/./index.html").path)
    assertEquals("assets/a+b.js", WebViewAssetPath.of("assets/a+b.js").path)
  }

  @Test
  fun rejectsTraversal() {
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("/webview/views/sample-panel/index.html") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("https://example.com/index.html") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("webview\\sample-panel\\index.html") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("../secret.txt") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("webview/../secret.txt") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("%2e%2e/secret.txt") }
  }

  @Test
  fun rejectsQueryAndFragment() {
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("index.html?theme=dark") }
    assertThrows<IllegalArgumentException> { WebViewAssetPath.of("index.html#main") }
  }
}
