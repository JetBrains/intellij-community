// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.ui

import com.jetbrains.python.packaging.toolwindow.ui.DescriptionRenderMode
import com.jetbrains.python.packaging.toolwindow.ui.decideDescriptionRender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

internal class PyPackageDescriptionRenderModeTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = [
    "text/markdown",
    "TEXT/MARKDOWN",
    "text/markdown; charset=UTF-8",
    "text/markdown; variant=GFM",
    "text/x-rst",
    "text/x-rst; charset=UTF-8",
    "  text/markdown  ",
  ])
  fun `rich render for markdown, rst, missing and whitespace-only content types`(contentType: String?) {
    assertEquals(DescriptionRenderMode.RICH, decideDescriptionRender(contentType))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "text/plain",
    "text/plain; charset=UTF-8",
    "text/html",
    "application/octet-stream",
    "unknown/garbage",
  ])
  fun `plain render for non-rich and unknown content types`(contentType: String) {
    assertEquals(DescriptionRenderMode.PLAIN, decideDescriptionRender(contentType))
  }
}
