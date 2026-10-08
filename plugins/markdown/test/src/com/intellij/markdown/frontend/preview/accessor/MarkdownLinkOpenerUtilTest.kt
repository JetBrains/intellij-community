// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.accessor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownLinkOpenerUtilTest {
  @Test
  fun `a second hash is encoded inside the fragment`() {
    val uri = MarkdownLinkOpenerUtil.createUri("https://example.com/path#first#second")
              ?: error("The link must produce a URI")

    assertEquals("first#second", uri.fragment)
    assertEquals("first%23second", uri.rawFragment)
  }

  @Test
  fun `a single hash is preserved`() {
    val uri = MarkdownLinkOpenerUtil.createUri("https://example.com/path#anchor")
              ?: error("The link must produce a URI")

    assertEquals("anchor", uri.fragment)
    assertEquals("anchor", uri.rawFragment)
  }

  @Test
  fun `a link without a scheme gets an http scheme`() {
    val uri = MarkdownLinkOpenerUtil.createUri("example.com/path#anchor")
              ?: error("The link must produce a URI")

    assertEquals("http", uri.scheme)
    assertEquals("example.com", uri.host)
    assertEquals("anchor", uri.fragment)
  }
}
