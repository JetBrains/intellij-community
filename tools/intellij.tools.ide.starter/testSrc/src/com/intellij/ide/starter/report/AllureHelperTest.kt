// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AllureHelperTest {
  @Test
  fun `MIME values are unique`() {
    val duplicates = AllureHelper.MimeType.entries.groupBy { it.value }.filterValues { it.size > 1 }

    assertTrue(duplicates.isEmpty(), "Duplicate MIME values: $duplicates")
  }

  @Test
  fun `file extensions are unique`() {
    val duplicates = AllureHelper.MimeType.entries.groupBy { it.ext }.filterValues { it.size > 1 }

    assertTrue(duplicates.isEmpty(), "Duplicate file extensions: $duplicates")
  }
}
