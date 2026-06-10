// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.test.env.uv.getUvArchivePrefixToStrip
import com.intellij.util.system.OS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class UvExecutableProviderTest {
  @Test
  fun `Windows uv archive is extracted without prefix stripping`() {
    assertNull(getUvArchivePrefixToStrip(OS.Windows, "uv-x86_64-pc-windows-msvc.zip"))
  }

  @Test
  fun `Unix uv archive is extracted from its root directory`() {
    assertEquals("uv-x86_64-unknown-linux-gnu", getUvArchivePrefixToStrip(OS.Linux, "uv-x86_64-unknown-linux-gnu.tar.gz"))
  }
}
