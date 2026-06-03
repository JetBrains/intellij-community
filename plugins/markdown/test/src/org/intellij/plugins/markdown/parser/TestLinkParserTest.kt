// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.parser

class TestLinkParserTest : MarkdownParsingTestCase("parser/testlink") {
  fun testBracketsOnly() = doTest(true)

  fun testWithTextPath() = doTest(true)

  fun testWithBacktickAfter() = doTest(true)

  fun testPrecededByText() = doTest(true)

  fun testWithRelativePath() = doTest(true)

  fun testTripleConsecutive() = doTest(true)

  fun testFollowedByAutolink() = doTest(true)

  fun testInlineLinkSyntax() = doTest(true)
}
