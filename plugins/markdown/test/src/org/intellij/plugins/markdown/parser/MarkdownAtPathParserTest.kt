// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownAtPathParserTest : MarkdownParsingTestCase("parser/atpath") {
  @Test
  fun testSimplePath() = parsing.doTest(true)

  @Test
  fun testTopLevelFile() = parsing.doTest(true)

  @Test
  fun testRelativePath() = parsing.doTest(true)

  @Test
  fun testMultiplePaths() = parsing.doTest(true)

  @Test
  fun testSurroundedByText() = parsing.doTest(true)

  @Test
  fun testFollowedByPunctuation() = parsing.doTest(true)

  @Test
  fun testInsideCodeSpan() = parsing.doTest(true)

  @Test
  fun testInsideInlineLink() = parsing.doTest(true)

  @Test
  fun testEmailAndMention() = parsing.doTest(true)

  @Test
  fun testAlongsideTestLink() = parsing.doTest(true)
}
