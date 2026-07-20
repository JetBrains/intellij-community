// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.wrap

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SlowOperations
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownInlayUpdateOnSoftWrapListener
import org.junit.jupiter.api.assertDoesNotThrow

class MarkdownSoftWrapTest : BasePlatformTestCase() {
  fun `test listener does not throw or report slow operations on EDT`() {
    myFixture.configureByText("test.md", "")
    val listener = MarkdownInlayUpdateOnSoftWrapListener()

    SlowOperations.reportKnownIssues()
    PlatformTestUtil.withSystemProperty<Nothing>(SlowOperations.FORBID_SLOW_OPS_PROPERTY, "true") {
      assertDoesNotThrow {
        SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use {
          listener.editorCreated(EditorFactoryEvent(EditorFactory.getInstance(), myFixture.editor))
        }
      }
    }
    assertTrue(SlowOperations.reportKnownIssues().isEmpty())
  }

  /**
   * The wrap candidate falls inside the link, where wrapping is forbidden, so the strategy has to move
   * the soft wrap before the link start. The link is placed after several blank-line-separated paragraphs to check
   * that [org.intellij.plugins.markdown.editor.MarkdownLineWrapPositionStrategy] resolves tokens correctly when
   * the lexed context does not start at the beginning of the document.
   */
  fun `test soft wrap does not break a link located after the first block`() {
    val filler = "Introductory paragraph that occupies space before the block under test.\n\n".repeat(3)
    val prefix = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu"
    val line = "$prefix [anchor](https://example.com/long/path/kept/intact) when the paragraph continues with several ordinary words"
    val text = filler + line + "\n"
    myFixture.configureByText("softWrapLink.md", text)
    assertTrue(EditorTestUtil.configureSoftWraps(myFixture.editor, 80))

    val lineStart = filler.length
    val linkStart = text.indexOf("[anchor]")
    val linkEnd = text.indexOf(')', linkStart) + 1
    val wrapOffsets = myFixture.editor.softWrapModel.getSoftWrapsForRange(0, text.length).map { it.start }
    assertNotEmpty(wrapOffsets)
    assertEmpty("soft wraps must not appear inside the inline link at [$linkStart, $linkEnd): $wrapOffsets",
                wrapOffsets.filter { it in (linkStart + 1) until linkEnd })
    assertTrue("expected the first visual row to wrap at or before the link start $linkStart: $wrapOffsets",
               wrapOffsets.any { it in (lineStart + 1)..linkStart })
  }
}
