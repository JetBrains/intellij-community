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

  @Test
  fun `an encoded Cyrillic fragment is decoded once`() {
    val uri = MarkdownLinkOpenerUtil.createUri("file:///C:/project/target.md#%D1%82%D0%B5%D1%81%D1%82")
              ?: error("The link must produce a URI")

    assertEquals("тест", uri.fragment)
    assertEquals("%D1%82%D0%B5%D1%81%D1%82", uri.rawFragment)
  }

  @Test
  fun `an encoded percent sign is preserved after decoding`() {
    val uri = MarkdownLinkOpenerUtil.createUri("file:///C:/project/target.md#%2520")
              ?: error("The link must produce a URI")

    assertEquals("%20", uri.fragment)
    assertEquals("%2520", uri.rawFragment)
  }
}
