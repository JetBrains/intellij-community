// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.intellij.markdown.frontend.preview.accessor

import com.intellij.markdown.frontend.preview.accessor.impl.MarkdownLinkOpenerImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MarkdownLocalLinkTest {
  @Test
  fun `an unresolved document link stays local`() {
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("missing.md#test", hasContainingFile = true))
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("subdir/missing.md", hasContainingFile = true))
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("C:/project/missing.md", hasContainingFile = true))
  }

  @Test
  fun `an explicit web link stays external`() {
    assertFalse(MarkdownLinkOpenerImpl.isLocalFilePathLink("https://example.com/path#test", hasContainingFile = true))
    assertFalse(MarkdownLinkOpenerImpl.isLocalFilePathLink("//example.com/path", hasContainingFile = true))
    assertFalse(MarkdownLinkOpenerImpl.isLocalFilePathLink("mailto:user@example.com", hasContainingFile = true))
  }

  @Test
  fun `a schemeless link without a document keeps its browser fallback`() {
    assertFalse(MarkdownLinkOpenerImpl.isLocalFilePathLink("example.com/path#test", hasContainingFile = false))
  }

  @Test
  fun `an explicit local path stays local without a document`() {
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("./missing.md", hasContainingFile = false))
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("../missing.md", hasContainingFile = false))
    assertTrue(MarkdownLinkOpenerImpl.isLocalFilePathLink("/project/missing.md", hasContainingFile = false))
  }
}
