// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FindInFilesArgumentTest {
  @Test
  fun `the queries without a directory take the project scope`() {
    assertEquals(FindInFilesArgument(null, false, listOf("foo", "bar baz")), parseFindInFilesArgument("foo;bar baz"))
  }

  @Test
  fun `the directory option comes before the queries`() {
    assertEquals(FindInFilesArgument("/work/project/platform", false, listOf("foo", "bar baz")),
                 parseFindInFilesArgument("-directory=/work/project/platform foo;bar baz"))
  }

  @Test
  fun `the headless option and the directory option come in any order`() {
    assertEquals(FindInFilesArgument("/work/p", true, listOf("foo")), parseFindInFilesArgument("-headless -directory=/work/p foo"))
    assertEquals(FindInFilesArgument("/work/p", true, listOf("foo")), parseFindInFilesArgument("-directory=/work/p -headless foo"))
    assertEquals(FindInFilesArgument(null, true, listOf("foo", "bar")), parseFindInFilesArgument("-headless foo;bar"))
  }
}
