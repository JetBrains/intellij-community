// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.parser

class MarkdownAtPathParserTest : MarkdownParsingTestCase("parser/atpath") {
  fun testSimplePath() = doTest(true)

  fun testTopLevelFile() = doTest(true)

  fun testRelativePath() = doTest(true)

  fun testMultiplePaths() = doTest(true)

  fun testSurroundedByText() = doTest(true)

  fun testFollowedByPunctuation() = doTest(true)

  fun testInsideCodeSpan() = doTest(true)

  fun testInsideInlineLink() = doTest(true)

  fun testEmailAndMention() = doTest(true)

  fun testAlongsideTestLink() = doTest(true)
}
