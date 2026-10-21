// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class TestLinkParserTest : MarkdownParsingTestCase("parser/testlink") {
  @Test
  fun testBracketsOnly() = parsing.doTest(true)

  @Test
  fun testWithTextPath() = parsing.doTest(true)

  @Test
  fun testWithBacktickAfter() = parsing.doTest(true)

  @Test
  fun testPrecededByText() = parsing.doTest(true)

  @Test
  fun testWithRelativePath() = parsing.doTest(true)

  @Test
  fun testTripleConsecutive() = parsing.doTest(true)

  @Test
  fun testFollowedByAutolink() = parsing.doTest(true)

  @Test
  fun testInlineLinkSyntax() = parsing.doTest(true)
}
